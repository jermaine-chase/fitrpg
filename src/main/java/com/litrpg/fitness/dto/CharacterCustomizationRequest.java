package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/character/{id}/customization}. Both
 * fields are cosmetic only — no gameplay effect. {@code titleAchievementCode}
 * is null (or blank) to unequip any title; otherwise it must be the code of
 * one of the character's own unlocked achievements.
 */
public class CharacterCustomizationRequest {

    @NotBlank
    private String avatarId;

    private String titleAchievementCode;

    public String getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(String avatarId) {
        this.avatarId = avatarId;
    }

    public String getTitleAchievementCode() {
        return titleAchievementCode;
    }

    public void setTitleAchievementCode(String titleAchievementCode) {
        this.titleAchievementCode = titleAchievementCode;
    }
}
