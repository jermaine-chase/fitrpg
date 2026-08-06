package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Achievement;

/**
 * A badge newly unlocked by the claim that just happened — returned inline
 * on {@code POST /api/character/{id}/claim} so the frontend can show an
 * unlock toast without a second round trip.
 */
public record AchievementUnlockDTO(String code, String name, String description, String icon) {

    public static AchievementUnlockDTO from(Achievement a) {
        return new AchievementUnlockDTO(a.getCode(), a.getName(), a.getDescription(), a.getIcon());
    }
}
