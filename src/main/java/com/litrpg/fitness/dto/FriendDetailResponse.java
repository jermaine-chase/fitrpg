package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.FriendVisibility;

import java.util.UUID;

/**
 * {@code GET /api/friends/{id}} — a friend's profile, trimmed to the
 * visibility level *they* granted the caller. {@code character} is null
 * when that level is {@code NONE}, or if they have not created a character.
 */
public class FriendDetailResponse {

    private UUID friendshipId;
    private UUID userId;
    private String username;
    private FriendVisibility visibility;
    private FriendCharacterSummary character;

    public FriendDetailResponse(UUID friendshipId, UUID userId, String username,
                                 FriendVisibility visibility, FriendCharacterSummary character) {
        this.friendshipId = friendshipId;
        this.userId = userId;
        this.username = username;
        this.visibility = visibility;
        this.character = character;
    }

    public UUID getFriendshipId() {
        return friendshipId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public FriendVisibility getVisibility() {
        return visibility;
    }

    public FriendCharacterSummary getCharacter() {
        return character;
    }
}
