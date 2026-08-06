package com.litrpg.fitness.model;

/**
 * Moderation state of a {@link Quest}. Admin-created quests are {@code APPROVED}
 * immediately; player-submitted quests (see {@code POST /api/quests/submit})
 * start {@code PENDING} and only appear in the public catalog once approved.
 */
public enum QuestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
