package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.WorkoutLog;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single feed entry: one friend's quest claim, trimmed to the visibility
 * level *they* have granted the viewer — same rule as {@link FriendCharacterSummary}.
 * Never constructed for a friend at {@code NONE} visibility.
 */
public class FriendActivityEntry {

    private UUID friendshipId;
    private String username;
    private String characterName;
    private String questTitle;
    private String statType;
    private Integer xpEarned;
    private LocalDateTime loggedAt;

    /** BASIC visibility: that a claim happened, with no quest or XP detail. */
    public static FriendActivityEntry basic(UUID friendshipId, String username, String characterName, WorkoutLog log) {
        FriendActivityEntry r = new FriendActivityEntry();
        r.friendshipId = friendshipId;
        r.username = username;
        r.characterName = characterName;
        r.loggedAt = log.getLoggedAt();
        return r;
    }

    /** FULL visibility: which quest, which attribute, and how much XP. */
    public static FriendActivityEntry full(UUID friendshipId, String username, String characterName, WorkoutLog log) {
        FriendActivityEntry r = basic(friendshipId, username, characterName, log);
        r.questTitle = log.getQuestTitle();
        r.statType = log.getStatType() != null ? log.getStatType().name() : null;
        r.xpEarned = log.getFinalXpAwarded();
        return r;
    }

    public UUID getFriendshipId() {
        return friendshipId;
    }

    public String getUsername() {
        return username;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getQuestTitle() {
        return questTitle;
    }

    public String getStatType() {
        return statType;
    }

    public Integer getXpEarned() {
        return xpEarned;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }
}
