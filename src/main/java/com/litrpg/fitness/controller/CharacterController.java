package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.AchievementResponse;
import com.litrpg.fitness.dto.CharacterCustomizationRequest;
import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.dto.ClaimQuestRequest;
import com.litrpg.fitness.dto.ClaimRewardResponse;
import com.litrpg.fitness.dto.CreateCharacterRequest;
import com.litrpg.fitness.dto.DailyQuestResponse;
import com.litrpg.fitness.dto.WorkoutLogEntryResponse;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.AchievementService;
import com.litrpg.fitness.service.CharacterService;
import com.litrpg.fitness.service.DailyQuestService;
import com.litrpg.fitness.service.GameEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * JSON REST API for characters and quest claims. Requires a player JWT
 * (see {@code /api/auth/**}); every operation is scoped to the calling
 * player's own character(s) — see {@link com.litrpg.fitness.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final CharacterService characterService;
    private final GameEngineService gameEngineService;
    private final DailyQuestService dailyQuestService;
    private final AchievementService achievementService;

    public CharacterController(CharacterService characterService,
                               GameEngineService gameEngineService,
                               DailyQuestService dailyQuestService,
                               AchievementService achievementService) {
        this.characterService = characterService;
        this.gameEngineService = gameEngineService;
        this.dailyQuestService = dailyQuestService;
        this.achievementService = achievementService;
    }

    /**
     * {@code POST /api/character}
     */
    @PostMapping
    public ResponseEntity<CharacterSheetResponse> createCharacter(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCharacterRequest request) {
        Character created = characterService.createCharacter(request.getCharacterName(), principal.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CharacterSheetResponse.from(created));
    }

    /**
     * Fetches the calling player's own character without knowing its id up
     * front — used by the login flow, which only has a user id after
     * authenticating. 404s if the account has no character yet.
     * {@code GET /api/character/mine}
     */
    @GetMapping("/mine")
    public ResponseEntity<CharacterSheetResponse> getMyCharacter(
            @AuthenticationPrincipal UserPrincipal principal) {
        Character character = characterService.getMyCharacter(principal.getId());
        return ResponseEntity.ok(CharacterSheetResponse.from(character));
    }

    /**
     * {@code GET /api/character/{id}}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterSheetResponse> getCharacter(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Character character = characterService.getOwnedCharacter(id, principal.getId());
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
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ClaimQuestRequest request) {
        // Ownership check before mutating; claimQuestRewards itself is owner-agnostic.
        characterService.getOwnedCharacter(id, principal.getId());
        ClaimRewardResponse result = gameEngineService.claimQuestRewards(id, request.getQuestId());
        return ResponseEntity.ok(result);
    }

    /**
     * The character's "Daily Focus" quest for today, assigned (and persisted)
     * on first request of the day if one doesn't already exist.
     * {@code GET /api/character/{id}/daily}
     */
    @GetMapping("/{id}/daily")
    public ResponseEntity<DailyQuestResponse> getDailyQuest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        characterService.getOwnedCharacter(id, principal.getId());
        return ResponseEntity.ok(dailyQuestService.getOrAssignDailyQuest(id));
    }

    /**
     * The owned character's claim history, most recent first — powers the
     * progress dashboard.
     * {@code GET /api/character/{id}/history}
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<WorkoutLogEntryResponse>> getWorkoutHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(characterService.getOwnedWorkoutHistory(id, principal.getId()));
    }

    /**
     * The owned character's full badge catalog, locked entries included.
     * {@code GET /api/character/{id}/achievements}
     */
    @GetMapping("/{id}/achievements")
    public ResponseEntity<List<AchievementResponse>> getAchievements(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        characterService.getOwnedCharacter(id, principal.getId());
        return ResponseEntity.ok(achievementService.getCatalogForCharacter(id));
    }

    /**
     * Sets the owned character's cosmetic avatar and equipped title (drawn
     * from its own unlocked achievements). No gameplay effect.
     * {@code PUT /api/character/{id}/customization}
     */
    @PutMapping("/{id}/customization")
    public ResponseEntity<CharacterSheetResponse> updateCustomization(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CharacterCustomizationRequest request) {
        Character updated = characterService.updateCustomization(
                id, principal.getId(), request.getAvatarId(), request.getTitleAchievementCode());
        return ResponseEntity.ok(CharacterSheetResponse.from(updated));
    }

    /**
     * Permanently deletes the character and all related data.
     * {@code DELETE /api/character/{id}}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCharacter(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        characterService.deleteOwnedCharacter(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
