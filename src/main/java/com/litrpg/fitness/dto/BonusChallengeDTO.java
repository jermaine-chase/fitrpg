package com.litrpg.fitness.dto;

/**
 * Optional bonus challenge awarded randomly on quest completion.
 * Present in the claim response only when the 15 % bonus roll fires.
 */
public record BonusChallengeDTO(String description, int bonusXp) {

}
