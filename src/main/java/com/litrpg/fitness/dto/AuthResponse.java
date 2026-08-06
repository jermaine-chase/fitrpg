package com.litrpg.fitness.dto;

public class AuthResponse {

    private String token;
    private String username;
    private boolean isAdmin;

    public AuthResponse() {
    }

    public AuthResponse(String token, String username, boolean isAdmin) {
        this.token = token;
        this.username = username;
        this.isAdmin = isAdmin;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }
}
