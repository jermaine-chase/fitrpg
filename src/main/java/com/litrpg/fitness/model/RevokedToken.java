package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A JWT that was explicitly logged out before its natural expiry. Maps to
 * the {@code revoked_tokens} table. Rows are only needed until
 * {@code expiresAt} passes — after that the token would be rejected as
 * expired anyway, so they're opportunistically pruned (see
 * {@code RevokedTokenRepository#deleteAllByExpiresAtBefore}).
 */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    /** The JWT's {@code jti} claim. */
    @Id
    @Column(name = "jti", updatable = false, nullable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public RevokedToken() {
    }

    public RevokedToken(String jti, LocalDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
