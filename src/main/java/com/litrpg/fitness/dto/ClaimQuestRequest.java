package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/character/{id}/claim}.
 *
 * <pre>{ "questId": "Q-1001" }</pre>
 */
public class ClaimQuestRequest {

    @NotBlank
    private String questId;

    public ClaimQuestRequest() {
    }

    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }
}
