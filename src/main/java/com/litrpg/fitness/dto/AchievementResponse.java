package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Achievement;

import java.time.LocalDateTime;

/**
 * A single row of the full badge catalog for a character, returned by
 * {@code GET /api/character/{id}/achievements} — includes locked entries so
 * the frontend can render a "?" placeholder for what's still to unlock.
 */
public record AchievementResponse(String code, String name, String description, String icon,
                                   boolean unlocked, LocalDateTime unlockedAt) {

    public static AchievementResponse locked(Achievement a) {
        return new AchievementResponse(a.getCode(), a.getName(), a.getDescription(), a.getIcon(), false, null);
    }

    public static AchievementResponse unlocked(Achievement a, LocalDateTime unlockedAt) {
        return new AchievementResponse(a.getCode(), a.getName(), a.getDescription(), a.getIcon(), true, unlockedAt);
    }
}
