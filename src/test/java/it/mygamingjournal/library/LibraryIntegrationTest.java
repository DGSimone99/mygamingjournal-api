package it.mygamingjournal.library;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LibraryIntegrationTest extends IntegrationTest {

    private void putEntry(TestUser user, long gameId, String json) throws Exception {
        mockMvc.perform(put("/api/me/library/" + gameId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void addingAGameReturnsTheEntryWithItsGame() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLAYING\",\"personalRating\":9.5,\"hoursPlayed\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawgGameId").value(3328))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.personalRating").value(9.5))
                .andExpect(jsonPath("$.notes").value(""))
                .andExpect(jsonPath("$.unlockedAchievements").value(0))
                .andExpect(jsonPath("$.game.name").value("Baldur's Gate 3"));
    }

    @Test
    void puttingTheSameGameAgainUpdatesInsteadOfDuplicating() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        putEntry(mario, 3328, "{\"status\":\"PLAYING\"}");
        putEntry(mario, 3328, "{\"status\":\"COMPLETED\",\"notes\":\"Great\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].notes").value("Great"));
    }

    @Test
    void unknownGameCannotBeAdded() throws Exception {
        TestUser mario = register("mario");
        when(rawgApi.getGame(999)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(put("/api/me/library/999").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ratingAboveTenIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\",\"personalRating\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("personalRating"));
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void libraryFiltersByStatusAndSortsByRating() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        RawgFixtures.stubGame(rawgApi, 2, "Two");
        RawgFixtures.stubGame(rawgApi, 3, "Three");
        putEntry(mario, 1, "{\"status\":\"COMPLETED\",\"personalRating\":6}");
        putEntry(mario, 2, "{\"status\":\"COMPLETED\",\"personalRating\":9}");
        putEntry(mario, 3, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("status", "COMPLETED").param("sort", "rating"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].rawgGameId").value(2))
                .andExpect(jsonPath("$.items[1].rawgGameId").value(1));
    }

    @Test
    void unsupportedSortIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)).param("sort", "name"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entryStillListedWhenRawgFailsForItsGame() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        putEntry(mario, 3328, "{\"status\":\"PLAYING\"}");
        catalogService.clearCaches();
        when(rawgApi.getGame(3328)).thenThrow(new ResourceAccessException("down"));

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rawgGameId").value(3328))
                .andExpect(jsonPath("$.items[0].game").value(nullValue()));
    }

    @Test
    void indexMapsGameIdsToStatus() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library/index").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value("WISHLIST"));
    }

    @Test
    void deletingRemovesTheEntryAndASecondDeleteIs404() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersOnlySeeTheirOwnLibrary() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void experienceAndLevelFollowTheLibrary() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");

        putEntry(mario, 1, "{\"status\":\"COMPLETED\",\"hoursPlayed\":10.5}");
        assertThat(experienceOf(mario)).isEqualTo(40 + 120 + 10 * 5);

        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        assertThat(experienceOf(mario)).isZero();
    }

    @Test
    void noDatabaseConnectionIsHeldWhileCallingRawg() throws Exception {
        TestUser mario = register("mario");
        AtomicInteger heldDuringRawg = stubGameRecordingHeldConnections(3328, "Baldur's Gate 3");

        putEntry(mario, 3328, "{\"status\":\"PLAYING\"}");
        catalogService.clearCaches();
        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.items[0].game.name").value("Baldur's Gate 3"));

        assertThat(heldDuringRawg.get()).isZero();
    }

    @Test
    void libraryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/library")).andExpect(status().isUnauthorized());
    }

    private Integer experienceOf(TestUser user) {
        return jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, user.id());
    }
}
