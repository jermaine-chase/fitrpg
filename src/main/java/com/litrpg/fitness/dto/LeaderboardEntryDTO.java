package com.litrpg.fitness.dto;

import java.util.UUID;

/**
 * A single ranked row from {@code GET /api/leaderboard}. {@code value} is
 * whatever the requested metric measures (level, lifetime XP, or streak).
 */
public record LeaderboardEntryDTO(int rank, UUID characterId, String characterName, String username, long value) {
}
