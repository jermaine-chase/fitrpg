package com.litrpg.fitness.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/character/{id}/claim}.
 * {@code bonusChallenge} is {@code null} when no bonus roll was triggered.
 * {@code dailyFocusBonusXp} is 0 unless the claimed quest was the character's
 * assigned Daily Focus quest for today. {@code newAchievements} is empty
 * unless this claim just unlocked one or more badges.
 */
public record ClaimRewardResponse(CharacterSheetResponse character, BonusChallengeDTO bonusChallenge,
                                   int dailyFocusBonusXp, List<AchievementUnlockDTO> newAchievements) {

}
