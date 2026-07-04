package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.dto.ClaimQuestRequest;
import com.litrpg.fitness.dto.ClaimRewardResponse;
import com.litrpg.fitness.dto.CreateCharacterRequest;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.service.CharacterService;
import com.litrpg.fitness.service.GameEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * JSON REST API for characters and quest claims.
 */
@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final CharacterService characterService;
    private final GameEngineService gameEngineService;

    public CharacterController(CharacterService characterService,
                               GameEngineService gameEngineService) {
        this.characterService = characterService;
        this.gameEngineService = gameEngineService;
    }

    /**
     * {@code POST /api/character}
     */
    @PostMapping
    public ResponseEntity<CharacterSheetResponse> createCharacter(
            @Valid @RequestBody CreateCharacterRequest request) {
        Character created = characterService.createCharacter(request.getCharacterName());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CharacterSheetResponse.from(created));
    }

    /**
     * {@code GET /api/character/{id}}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterSheetResponse> getCharacter(@PathVariable UUID id) {
        Character character = characterService.getCharacter(id);
        return ResponseEntity.ok(CharacterSheetResponse.from(character));
    }

    /**
     * Claims rewards for a completed quest.
     * Returns the updated character sheet plus an optional bonus challenge
     * when the 15 % random roll fires.
     * {@code POST /api/character/{id}/claim}
     */
    @PostMapping("/{id}/claim")
    public ResponseEntity<ClaimRewardResponse> claimQuest(
            @PathVariable UUID id,
            @Valid @RequestBody ClaimQuestRequest request) {
        ClaimRewardResponse result = gameEngineService.claimQuestRewards(id, request.getQuestId());
        return ResponseEntity.ok(result);
    }

    /**
     * Permanently deletes the character and all related data.
     * {@code DELETE /api/character/{id}}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable UUID id) {
        characterService.deleteCharacter(id);
        return ResponseEntity.noContent().build();
    }
}
