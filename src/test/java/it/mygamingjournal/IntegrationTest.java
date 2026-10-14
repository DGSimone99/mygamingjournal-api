package it.mygamingjournal;

import com.jayway.jsonpath.JsonPath;
import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.RawgApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected RawgApi rawgApi;

    @Autowired
    protected CatalogService catalogService;

    @BeforeEach
    void clearCatalogCaches() {
        // The Spring context (and its caches) is shared between test classes.
        catalogService.clearCaches();
    }

    @AfterEach
    void cleanDatabase() {
        // Every user-owned table references users, so the cascade empties them all.
        jdbcTemplate.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }

    protected TestUser register(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","password":"password123"}
                """.formatted(username, username);
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.user.id");
        String token = JsonPath.read(json, "$.accessToken");
        return new TestUser(id.longValue(), token);
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    public record TestUser(long id, String token) {
    }
}
