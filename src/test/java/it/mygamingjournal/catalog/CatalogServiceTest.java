package it.mygamingjournal.catalog;

import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogServiceTest {

    private static final String BASE = "https://rawg.test/api";

    private static final String GAME_JSON = """
            {"id":3328,"slug":"bg3","name":"Baldur's Gate 3","description_raw":"Party up","released":"2023-08-03",
             "background_image":"https://media.rawg.io/bg3.jpg","rating":4.5,"metacritic":96,
             "esrb_rating":{"id":4,"name":"Mature","slug":"mature"},"reddit_url":"","achievements_count":54,
             "genres":[{"id":5,"name":"RPG","slug":"role-playing-games-rpg"}],
             "parent_platforms":[{"platform":{"id":1,"name":"PC","slug":"pc"}}],
             "platforms":[{"platform":{"id":4,"name":"PC","slug":"pc"},"requirements":{"minimum":"8 GB","recommended":"16 GB"}}],
             "developers":[{"id":10,"name":"Larian Studios","slug":"larian-studios"}],
             "publishers":[],"tags":[],"unknown_field":true}
            """;
    private static final String SCREENSHOTS_JSON = """
            {"count":1,"next":null,"results":[{"id":1,"image":"https://media.rawg.io/shot.jpg"}]}
            """;

    private MockRestServiceServer server;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        service = createService("test-key");
    }

    private CatalogService createService(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RawgProperties properties = new RawgProperties(BASE, apiKey);
        return new CatalogService(RawgClientConfig.createRawgApi(builder, properties), properties);
    }

    private void expectGame(long id, ExpectedCount count) {
        server.expect(count, requestTo(BASE + "/games/" + id + "?key=test-key"))
                .andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));
        server.expect(count, requestTo(BASE + "/games/" + id + "/screenshots?key=test-key"))
                .andRespond(withSuccess(SCREENSHOTS_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    void getGameMapsDetailsAndAppendsTheKey() {
        expectGame(3328, ExpectedCount.once());

        GameDetails details = service.getGame(3328);

        assertThat(details.name()).isEqualTo("Baldur's Gate 3");
        assertThat(details.requirements().minimum()).isEqualTo("8 GB");
        assertThat(details.screenshots()).containsExactly("https://media.rawg.io/shot.jpg");
        server.verify();
    }

    @Test
    void secondGetGameIsServedFromCache() {
        expectGame(3328, ExpectedCount.once());

        service.getGame(3328);
        service.getGame(3328);

        server.verify();
    }

    @Test
    void unknownGameIsNotFound() {
        server.expect(requestTo(BASE + "/games/999?key=test-key")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getGame(999))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Game 999 not found");
    }

    @Test
    void achievementsAreReadAcrossAllPages() {
        server.expect(requestTo(BASE + "/games/3328/achievements?page=1&page_size=40&key=test-key"))
                .andRespond(withSuccess("""
                        {"count":2,"next":"https://api.rawg.io/api/games/3328/achievements?page=2",
                         "results":[{"id":1,"name":"First","description":"d","image":"i","percent":"50.0"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/games/3328/achievements?page=2&page_size=40&key=test-key"))
                .andRespond(withSuccess("""
                        {"count":2,"next":null,
                         "results":[{"id":2,"name":"Second","description":"d","image":"i","percent":"5.5"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GameAchievement> achievements = service.getAchievements(3328);

        assertThat(achievements).extracting(GameAchievement::name).containsExactly("First", "Second");
        server.verify();
    }

    @Test
    void searchPagePastTheEndIsEmptyInsteadOfNotFound() {
        server.expect(requestTo(BASE + "/games?page=99&page_size=20&key=test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PageResponse<GameSummary> page = service.searchGames(
                new GameQuery(null, null, null, null, null, null), new PageParams(99, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(99);
    }

    @Test
    void missingKeyFailsWithoutCallingRawg() {
        CatalogService keyless = createService("");

        assertThatThrownBy(() -> keyless.getGame(3328))
                .isInstanceOf(RawgUnavailableException.class)
                .hasMessage("RAWG API key not configured");
        server.verify();
    }

    @Test
    void rejectedKeyIsUnavailable() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUnavailableException.class)
                .hasMessage("RAWG API key is invalid");
    }

    @Test
    void rateLimitIsUnavailable() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.getGame(3328)).isInstanceOf(RawgUnavailableException.class);
    }

    @Test
    void serverErrorIsUpstreamFailure() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withServerError());

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUpstreamException.class)
                .hasMessageNotContaining("test-key");
    }

    @Test
    void timeoutIsUpstreamFailure() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUpstreamException.class)
                .hasMessage("RAWG is not reachable");
    }

    @Test
    void summariesSkipGamesThatFail() {
        expectGame(3328, ExpectedCount.once());
        server.expect(requestTo(BASE + "/games/1?key=test-key")).andRespond(withServerError());

        Map<Long, GameSummary> summaries = service.getSummaries(List.of(3328L, 1L));

        assertThat(summaries).containsOnlyKeys(3328L);
        assertThat(summaries.get(3328L).name()).isEqualTo("Baldur's Gate 3");
    }
}
