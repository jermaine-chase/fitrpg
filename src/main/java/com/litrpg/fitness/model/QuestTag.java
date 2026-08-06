package com.litrpg.fitness.model;

/**
 * Category chip shown on the quest board, used for player filtering.
 * Nullable on {@link Quest} — untagged quests predate this feature.
 */
public enum QuestTag {
    QUICK,
    INTENSE,
    RECOVERY,
    STRENGTH,
    CARDIO
}
