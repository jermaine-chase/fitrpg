package com.litrpg.fitness.model;

/**
 * How an {@link Achievement}'s {@code threshold} is evaluated against a
 * character's current state at claim time. See {@code AchievementService}.
 */
public enum AchievementCriteriaType {
    /** Unlocked the moment a character's lifetime claim count reaches the threshold (typically 1). */
    FIRST_CLAIM,
    /** Unlocked when {@code currentLevel >= threshold}. */
    LEVEL_MILESTONE,
    /** Unlocked when {@code streakCount >= threshold}. */
    STREAK_MILESTONE,
    /** Unlocked when the character's lifetime claim count reaches the threshold. */
    TOTAL_CLAIMS_MILESTONE,
    /** Unlocked when all four stats are simultaneously at or above the threshold level. */
    ALL_STATS_LEVEL
}
