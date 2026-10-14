package it.mygamingjournal.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-012345";
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private JwtService serviceAt(Instant instant) {
        return new JwtService(new JwtProperties(SECRET, Duration.ofDays(7)), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void tokenRoundTripsTheUserId() {
        JwtService service = serviceAt(NOW);
        assertThat(service.parseUserId(service.generateToken(42L))).contains(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = serviceAt(NOW);
        String token = service.generateToken(42L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(service.parseUserId(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(NOW).generateToken(42L);
        assertThat(serviceAt(NOW.plus(Duration.ofDays(8))).parseUserId(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW).parseUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void tokensAreSignedWithHs256EvenWithALongSecret() {
        String longSecret = "x".repeat(64); // would select HS512 if the algorithm were derived from key size
        JwtService service = new JwtService(new JwtProperties(longSecret, Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        String header = new String(Base64.getUrlDecoder().decode(service.generateToken(1L).split("\\.")[0]),
                StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", Duration.ofDays(7)), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
