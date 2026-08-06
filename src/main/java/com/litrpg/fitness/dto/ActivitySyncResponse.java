package com.litrpg.fitness.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/character/{id}/activity-sync}. Empty
 * {@code rewards} means the submitted activity didn't cross either XP
 * threshold — the raw data is still stored for audit, but nothing was
 * awarded.
 */
public record ActivitySyncResponse(CharacterSheetResponse character, List<ActivityRewardDTO> rewards,
                                    List<AchievementUnlockDTO> newAchievements) {
}
