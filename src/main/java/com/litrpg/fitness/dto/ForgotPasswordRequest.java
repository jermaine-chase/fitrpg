package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

    @NotBlank
    private String username;

    public ForgotPasswordRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
