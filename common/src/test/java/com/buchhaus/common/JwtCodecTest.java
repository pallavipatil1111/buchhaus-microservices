package com.buchhaus.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtCodecTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-10-14T04:30:00Z");
    private static final AuthUser ADMIN = new AuthUser(42L, "pallavi@example.com", Role.ADMIN);

    private final JwtCodec codec = new JwtCodec(SECRET, Duration.ofMinutes(60));

    @Test
    void issuedTokenVerifiesBackToTheSameUserAndRole() {
        assertThat(codec.verify(codec.issue(ADMIN, NOW), NOW.plusSeconds(60))).contains(ADMIN);
    }

    @Test
    void expiredTokenIsRejected() {
        assertThat(codec.verify(codec.issue(ADMIN, NOW), NOW.plus(Duration.ofMinutes(61)))).isEmpty();
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = codec.issue(ADMIN, NOW);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThat(codec.verify(tampered, NOW)).isEmpty();
        JwtCodec other = new JwtCodec("another-secret-another-secret-another-1", Duration.ofMinutes(60));
        assertThat(codec.verify(other.issue(ADMIN, NOW), NOW)).isEmpty();
        assertThat(codec.verify("not-a-jwt", NOW)).isEmpty();
    }

    @Test
    void shortSecretsAreRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtCodec("too-short", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyAdminsPassTheAdminCheck() {
        CallerHeaders.requireAdmin("ADMIN");
        assertThatThrownBy(() -> CallerHeaders.requireAdmin("CUSTOMER")).isInstanceOf(ApiException.class)
                .hasMessage("Admins only");
        assertThatThrownBy(() -> CallerHeaders.requireAdmin(null)).isInstanceOf(ApiException.class);
    }
}
