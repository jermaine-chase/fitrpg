package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.WorkoutLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single entry in a character's workout history, used to render the
 * progress dashboard. Built as a DTO rather than serializing {@link WorkoutLog}
 * directly to avoid exposing the lazy {@code character} relationship.
 */
public class WorkoutLogEntryResponse {

    private UUID id;
    private String questId;
    private String questTitle;
    private String statType;
    private int baseXpEarned;
    private BigDecimal multiplierApplied;
    private int finalXpAwarded;
    private LocalDateTime loggedAt;

    public static WorkoutLogEntryResponse from(WorkoutLog log) {
        WorkoutLogEntryResponse r = new WorkoutLogEntryResponse();
        r.id = log.getId();
        r.questId = log.getQuestId();
        r.questTitle = log.getQuestTitle();
        r.statType = log.getStatType() != null ? log.getStatType().name() : null;
        r.baseXpEarned = log.getBaseXpEarned();
        r.multiplierApplied = log.getMultiplierApplied();
        r.finalXpAwarded = log.getFinalXpAwarded();
        r.loggedAt = log.getLoggedAt();
        return r;
    }

    public UUID getId() {
        return id;
    }

    public String getQuestId() {
        return questId;
    }

    public String getQuestTitle() {
        return questTitle;
    }

    public String getStatType() {
        return statType;
    }

    public int getBaseXpEarned() {
        return baseXpEarned;
    }

    public BigDecimal getMultiplierApplied() {
        return multiplierApplied;
    }

    public int getFinalXpAwarded() {
        return finalXpAwarded;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }
}
