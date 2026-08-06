package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.FriendVisibility;

/**
 * {@code GET/PUT /api/friends/settings} — the caller's default visibility
 * level, applied to any friend without a per-friendship override.
 */
public class FriendSettingsResponse {

    private FriendVisibility defaultVisibility;

    public FriendSettingsResponse() {
    }

    public FriendSettingsResponse(FriendVisibility defaultVisibility) {
        this.defaultVisibility = defaultVisibility;
    }

    public FriendVisibility getDefaultVisibility() {
        return defaultVisibility;
    }

    public void setDefaultVisibility(FriendVisibility defaultVisibility) {
        this.defaultVisibility = defaultVisibility;
    }
}
