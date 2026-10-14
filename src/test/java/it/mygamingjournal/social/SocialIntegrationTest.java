package it.mygamingjournal.social;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SocialIntegrationTest extends IntegrationTest {

    @Test
    void followIsIdempotentAndListed() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("luigi"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void followIsOneWay() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unfollowIsIdempotent() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void followingYourselfIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/me/following/" + mario.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot follow yourself"));
    }

    @Test
    void followingAnUnknownUserIs404() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/me/following/9999").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
    }
}
