package it.mygamingjournal.matchmaking;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchmakingIntegrationTest extends IntegrationTest {

    private TestUser mario;
    private TestUser luigi;
    private TestUser peach;

    @BeforeEach
    void setUpPlayers() throws Exception {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        mario = register("mario");
        luigi = register("luigi");
        peach = register("peach");
        for (TestUser user : new TestUser[]{mario, luigi, peach}) {
            mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                    .andExpect(status().isOk());
        }
    }

    private void setAvailability(TestUser user, String json) throws Exception {
        mockMvc.perform(put("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void availablePlayersExcludeMeAndFilterByPlatformAndLanguage() throws Exception {
        setAvailability(mario, "{\"platforms\":[\"pc\"],\"languages\":[\"it\"]}");
        setAvailability(luigi, "{\"platforms\":[\"pc\",\"playstation\"],\"languages\":[\"en\"]}");
        setAvailability(peach, "{\"platforms\":[\"xbox\"],\"languages\":[\"it\"]}");

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].user.username").value("luigi"))
                .andExpect(jsonPath("$.items[1].user.username").value("peach"));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("platform", "pc"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].user.username").value("luigi"))
                .andExpect(jsonPath("$.items[0].platforms[1]").value("playstation"));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("language", "it"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].user.username").value("peach"));
    }

    @Test
    void expiredAvailabilityIsHidden() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");
        // Two days back, so the local-vs-UTC date difference around midnight cannot make this flaky.
        jdbcTemplate.update("update play_availability set available_until = ?", LocalDate.now().minusDays(2));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void availabilityShowsOnTheLibraryEntryAndCanBeRemoved() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.items[0].availability.platforms[0]").value("pc"));

        mockMvc.perform(delete("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void pastDateAndEmptyPlatformsAreRejected() throws Exception {
        mockMvc.perform(put("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platforms\":[],\"languages\":[\"en\"],\"availableUntil\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void availabilityNeedsTheGameInLibrary() throws Exception {
        mockMvc.perform(put("/api/me/library/1/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void matchmakingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/games/3328/available-players")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountsLeaveMatchmaking() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
