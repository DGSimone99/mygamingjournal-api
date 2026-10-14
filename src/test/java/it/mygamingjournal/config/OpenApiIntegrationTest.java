package it.mygamingjournal.config;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiIntegrationTest extends IntegrationTest {

    @Test
    void apiDocsDescribeTheApiAndTheBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MyGamingJournal API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/me/library/{rawgGameId}']").exists())
                .andExpect(jsonPath("$.paths['/api/games/{rawgGameId}/available-players']").exists());
    }
}
