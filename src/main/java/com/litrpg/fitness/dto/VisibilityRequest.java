package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.FriendVisibility;
import jakarta.validation.constraints.NotNull;

/**
 * Body for setting a default or per-friend visibility level:
 * {@code PUT /api/friends/settings} and {@code PUT /api/friends/{id}/visibility}.
 */
public class VisibilityRequest {

    @NotNull(message = "visibility is required")
    private FriendVisibility visibility;

    public FriendVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(FriendVisibility visibility) {
        this.visibility = visibility;
    }
}
