package it.mygamingjournal.config;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

    @Test
    void apiDocsAreShapedForTypedClients() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/games'].get.parameters[*].name",
                        hasItems("search", "genre", "ordering", "page", "pageSize")))
                .andExpect(jsonPath("$.paths['/api/games'].get.parameters[*].name", not(hasItem("paging"))))
                .andExpect(jsonPath("$.paths['/api/genres'].get.responses['200'].content['application/json']").exists())
                .andExpect(jsonPath("$.components.schemas.GameSummary.required", hasItems("id", "coverImage")))
                .andExpect(jsonPath("$.components.schemas.UpsertEntryRequest.required", contains("status")));
    }
}
