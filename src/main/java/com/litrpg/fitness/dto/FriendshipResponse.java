package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.FriendVisibility;
import com.litrpg.fitness.model.Friendship;
import com.litrpg.fitness.model.FriendshipStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A friendship or friend-request entry, from the perspective of the calling
 * user — {@code userId}/{@code username} always describe the *other* party.
 */
public class FriendshipResponse {

    private UUID friendshipId;
    private UUID userId;
    private String username;
    private FriendshipStatus status;
    private LocalDateTime createdAt;
    /** What the caller has granted this friend (their override, else their default). Only set on the friends list. */
    private FriendVisibility visibilityGranted;

    public static FriendshipResponse from(Friendship friendship, UUID viewerId, String otherUsername) {
        FriendshipResponse r = new FriendshipResponse();
        r.friendshipId = friendship.getId();
        r.userId = friendship.otherUser(viewerId);
        r.username = otherUsername;
        r.status = friendship.getStatus();
        r.createdAt = friendship.getCreatedAt();
        return r;
    }

    public static FriendshipResponse withVisibility(Friendship friendship, UUID viewerId, String otherUsername,
                                                      FriendVisibility visibilityGranted) {
        FriendshipResponse r = from(friendship, viewerId, otherUsername);
        r.visibilityGranted = visibilityGranted;
        return r;
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

    public FriendshipStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public FriendVisibility getVisibilityGranted() {
        return visibilityGranted;
    }
}
