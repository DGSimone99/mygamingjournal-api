package it.mygamingjournal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextTest extends IntegrationTest {

    @Test
    void flywayCreatesTheV2Schema() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("users", "user_roles", "user_languages", "user_gaming_accounts",
                "user_follows", "game_entries", "achievement_progress", "play_availability", "reviews",
                "flyway_schema_history");
        assertThat(tables).doesNotContain("games", "achievements", "related_games");
    }
}
