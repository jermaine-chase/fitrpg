package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new character (and seeding its four stats).
 */
public class CreateCharacterRequest {

    @NotBlank
    @Size(max = 255)
    private String characterName;

    public CreateCharacterRequest() {
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
}
