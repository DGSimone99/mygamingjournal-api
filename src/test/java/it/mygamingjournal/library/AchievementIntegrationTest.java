package it.mygamingjournal.library;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AchievementIntegrationTest extends IntegrationTest {

    private TestUser mario;

    @BeforeEach
    void addGameToLibrary() throws Exception {
        mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        RawgFixtures.stubAchievements(rawgApi, 3328,
                RawgFixtures.achievement(101, "Descent"), RawgFixtures.achievement(102, "Ascent"));
        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isOk());
    }

    private void unlock(long achievementId) throws Exception {
        mockMvc.perform(put("/api/me/library/3328/achievements/" + achievementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listMergesRawgAchievementsWithProgress() throws Exception {
        unlock(102);

        mockMvc.perform(get("/api/me/library/3328/achievements").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].unlocked").value(false))
                .andExpect(jsonPath("$[0].unlockedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].name").value("Ascent"))
                .andExpect(jsonPath("$[1].unlocked").value(true))
                .andExpect(jsonPath("$[1].percent").value(12.5));
    }

    @Test
    void unlockIsIdempotentAndCountsOnce() throws Exception {
        unlock(101);
        unlock(101);

        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.items[0].unlockedAchievements").value(1));
    }

    @Test
    void lockingIsIdempotent() throws Exception {
        unlock(101);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/me/library/3328/achievements/101").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isZero();
    }

    @Test
    void unlockingAddsExperience() throws Exception {
        int before = jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, mario.id());
        unlock(101);
        int after = jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, mario.id());
        assertThat(after - before).isEqualTo(15);
    }

    @Test
    void achievementOfAnotherGameIs404() throws Exception {
        mockMvc.perform(put("/api/me/library/3328/achievements/999").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Achievement 999 not found for game 3328"));
    }

    @Test
    void gameNotInLibraryIs404() throws Exception {
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/library/3328/achievements/101").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game is not in your library"));
    }

    @Test
    void removingTheGameRemovesItsProgress() throws Exception {
        unlock(101);
        mockMvc.perform(delete("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isZero();
    }
}
