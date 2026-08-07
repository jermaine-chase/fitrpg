package com.litrpg.fitness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The calling player's own account info, read fresh from the database.
 * Used by the frontend on page load (when there's no fresh login response
 * to read {@code isAdmin} from) to decide whether to show admin-only UI.
 */
public class MeResponse {

    private String username;
    private boolean isAdmin;

    public MeResponse() {
    }

    public MeResponse(String username, boolean isAdmin) {
        this.username = username;
        this.isAdmin = isAdmin;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonProperty("isAdmin")
    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }
}
