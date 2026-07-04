package com.litrpg.fitness.dto;

/**
 * Response body for {@code POST /api/character/{id}/claim}.
 * {@code bonusChallenge} is {@code null} when no bonus roll was triggered.
 */
public record ClaimRewardResponse(CharacterSheetResponse character, BonusChallengeDTO bonusChallenge) {

}
