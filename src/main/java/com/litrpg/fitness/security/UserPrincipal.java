package com.litrpg.fitness.security;

import java.util.UUID;

/**
 * Authenticated player identity extracted from a validated JWT, set as the
 * {@code Authentication} principal for {@code /api/character/**} requests.
 */
public class UserPrincipal {

    private final UUID id;
    private final String username;

    public UserPrincipal(UUID id, String username) {
        this.id = id;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}
