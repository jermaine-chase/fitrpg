package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/friends/requests} — who to send a friend request to.
 */
public class FriendRequestDTO {

    @NotBlank(message = "username is required")
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
