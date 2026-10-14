package it.mygamingjournal.review;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTest {

    @BeforeEach
    void stubGame() {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
    }

    private void review(TestUser user, String text, int score) throws Exception {
        review(user, text, score, 3328);
    }

    private void review(TestUser user, String text, int score, long gameId) throws Exception {
        mockMvc.perform(put("/api/games/" + gameId + "/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"%s\",\"score\":%d}".formatted(text, score)))
                .andExpect(status().isOk());
    }

    @Test
    void oneReviewPerUserPerGame() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        review(mario, "Actually great", 10);

        mockMvc.perform(get("/api/games/3328/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].text").value("Actually great"))
                .andExpect(jsonPath("$.items[0].score").value(10))
                .andExpect(jsonPath("$.items[0].author.username").value("mario"))
                .andExpect(jsonPath("$.items[0].game").value(nullValue()));
    }

    @Test
    void scoreOutOfRangeIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Bad\",\"score\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("score"));
    }

    @Test
    void writingRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/games/3328/reviews/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Good\",\"score\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/games/3328/reviews")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void userReviewsIncludeTheGame() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);

        mockMvc.perform(get("/api/users/" + mario.id() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].game.name").value("Baldur's Gate 3"));
        mockMvc.perform(get("/api/users/9999/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void gameDetailsCarryCommunityStats() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        review(mario, "Good", 7);
        review(luigi, "Great", 10);
        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(jsonPath("$.community.averageScore").value(8.5))
                .andExpect(jsonPath("$.community.reviewCount").value(2))
                .andExpect(jsonPath("$.community.libraryCount").value(1));
    }

    @Test
    void noDatabaseConnectionIsHeldWhileCallingRawg() throws Exception {
        TestUser mario = register("mario");
        AtomicInteger heldDuringRawg = stubGameRecordingHeldConnections(1, "One");

        review(mario, "Good", 7, 1);
        catalogService.clearCaches();
        mockMvc.perform(get("/api/users/" + mario.id() + "/reviews"))
                .andExpect(jsonPath("$.items[0].game.name").value("One"));

        assertThat(heldDuringRawg.get()).isZero();
    }

    @Test
    void ownReviewCanBeRead() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(get("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("You have not reviewed this game"));
        review(mario, "Good", 7);

        mockMvc.perform(get("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Good"))
                .andExpect(jsonPath("$.author.username").value("mario"));
    }

    @Test
    void deletedAccountsReviewsDisappear() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/games/3328/reviews")).andExpect(jsonPath("$.totalItems").value(0));
    }
}
