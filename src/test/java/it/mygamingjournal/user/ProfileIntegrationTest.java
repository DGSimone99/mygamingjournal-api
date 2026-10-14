package it.mygamingjournal.user;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends IntegrationTest {

    @Test
    void meReturnsProfileAndStats() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\",\"hoursPlayed\":4}"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mario"))
                .andExpect(jsonPath("$.email").value("mario@example.com"))
                .andExpect(jsonPath("$.stats.totalGames").value(1))
                .andExpect(jsonPath("$.stats.completedGames").value(1))
                .andExpect(jsonPath("$.stats.hoursPlayed").value(4.0))
                .andExpect(jsonPath("$.experience").value(40 + 120 + 20));
    }

    @Test
    void patchUpdatesOnlyTheGivenFields() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Super Mario","languages":["it","en"],
                                 "gamingAccounts":{"steam":"mario64","discord":" "}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Super Mario"))
                .andExpect(jsonPath("$.languages", contains("en", "it")))
                .andExpect(jsonPath("$.gamingAccounts.steam").value("mario64"))
                .andExpect(jsonPath("$.gamingAccounts.discord").value(nullValue()));

        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Plumber\"}"))
                .andExpect(jsonPath("$.bio").value("Plumber"))
                .andExpect(jsonPath("$.displayName").value("Super Mario"))
                .andExpect(jsonPath("$.gamingAccounts.steam").value("mario64"));
    }

    @Test
    void invalidLanguageCodeIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"languages\":[\"Italian\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));

        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"mario\",\"password\":\"new-password-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userSearchMatchesUsernameOrDisplayNameAndIsPublic() throws Exception {
        TestUser mario = register("mario");
        register("luigi");
        register("peach");
        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Red Plumber\"}"));

        mockMvc.perform(get("/api/users").param("search", "PLUMB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("mario"));
        mockMvc.perform(get("/api/users").param("search", "%"))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/users"))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void publicProfileShowsFollowedByMeOnlyWhenAuthenticated() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        mockMvc.perform(get("/api/users/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("luigi"))
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/users/" + luigi.id()))
                .andExpect(jsonPath("$.followedByMe").value(nullValue()));
    }

    @Test
    void publicLibraryIsReadableAndUnknownUserIs404() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));

        mockMvc.perform(get("/api/users/" + mario.id() + "/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].game.name").value("One"));
        mockMvc.perform(get("/api/users/9999/library")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/9999")).andExpect(status().isNotFound());
    }

    @Test
    void deletingTheAccountCascadesEverywhere() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));
        mockMvc.perform(put("/api/me/following/" + mario.id()).header(HttpHeaders.AUTHORIZATION, bearer(luigi)));

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_entries", Integer.class)).isZero();
    }
}
