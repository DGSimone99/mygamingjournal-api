package it.mygamingjournal;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.RawgApi;
import it.mygamingjournal.catalog.RawgFixtures;
import it.mygamingjournal.media.ImageStorage;
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

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.when;
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

    @Autowired
    protected DataSource dataSource;

    @MockitoBean
    protected RawgApi rawgApi;

    @MockitoBean
    protected ImageStorage imageStorage;

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

    /**
     * Stubs a RAWG game whose calls record how many pooled DB connections are checked out at that moment.
     * The returned counter holds the highest value seen; 0 means no transaction was held during RAWG calls.
     */
    protected AtomicInteger stubGameRecordingHeldConnections(long id, String name) throws Exception {
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        AtomicInteger maxHeld = new AtomicInteger();
        when(rawgApi.getGame(id)).thenAnswer(invocation -> {
            maxHeld.accumulateAndGet(pool.getActiveConnections(), Math::max);
            return RawgFixtures.gameDetails(id, name);
        });
        when(rawgApi.getScreenshots(id)).thenReturn(RawgFixtures.screenshots(id));
        return maxHeld;
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    public record TestUser(long id, String token) {
    }
}
