package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.StatType;

/** One stat's worth of XP awarded by an activity sync, mirroring a quest claim's reward line. */
public record ActivityRewardDTO(StatType stat, int xpAwarded, BonusChallengeDTO bonusChallenge) {
}
