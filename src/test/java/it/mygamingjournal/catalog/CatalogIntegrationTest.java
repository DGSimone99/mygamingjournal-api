package it.mygamingjournal.catalog;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends IntegrationTest {

    @Test
    void searchIsPublicAndForwardsFilters() throws Exception {
        when(rawgApi.listGames(argThat((Map<String, String> params) ->
                "action".equals(params.get("genres")) && "-rating".equals(params.get("ordering")))))
                .thenReturn(RawgFixtures.page(List.of(RawgFixtures.game(3328, "Baldur's Gate 3")), null));

        mockMvc.perform(get("/api/games").param("genre", "action").param("ordering", "-rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(3328))
                .andExpect(jsonPath("$.items[0].name").value("Baldur's Gate 3"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void invalidOrderingIs400() throws Exception {
        mockMvc.perform(get("/api/games").param("ordering", "drop table"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ordering"));
    }

    @Test
    void nonNumericGameIdIs400() throws Exception {
        mockMvc.perform(get("/api/games/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void gameDetailsArePublic() throws Exception {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Baldur's Gate 3"))
                .andExpect(jsonPath("$.community.reviewCount").value(0));
    }

    @Test
    void rawgOutageIs502ProblemDetail() throws Exception {
        when(rawgApi.getGame(anyLong())).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("RAWG is not reachable"));
    }

    @Test
    void genresAreListed() throws Exception {
        when(rawgApi.getGenres(40)).thenReturn(RawgFixtures.page(
                List.of(new RawgModels.RawgNamed(4, "Action", "action")), null));

        mockMvc.perform(get("/api/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("action"));
    }
}
