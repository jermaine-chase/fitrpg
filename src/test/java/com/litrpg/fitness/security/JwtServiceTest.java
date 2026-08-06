package com.litrpg.fitness.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-long-enough-for-hs256-signing", 60_000L);

    @Test
    void generateToken_roundTripsClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "hero", true);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("hero");
        assertThat(claims.get("uid", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("admin", Boolean.class)).isTrue();
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void generateToken_nonAdminClaimIsFalse() {
        String token = jwtService.generateToken(UUID.randomUUID(), "sidekick", false);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.get("admin", Boolean.class)).isFalse();
    }

    @Test
    void parseClaims_rejectsExpiredToken() {
        JwtService shortLived = new JwtService("test-secret-key-that-is-long-enough-for-hs256-signing", -1000L);
        String token = shortLived.generateToken(UUID.randomUUID(), "hero", false);

        assertThatThrownBy(() -> shortLived.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseClaims_rejectsTamperedToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "hero", false);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseClaims(tampered)).isInstanceOf(RuntimeException.class);
    }
}
