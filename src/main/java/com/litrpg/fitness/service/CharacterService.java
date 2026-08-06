package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AdminCharacterUpdateRequest;
import com.litrpg.fitness.dto.WorkoutLogEntryResponse;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterAchievementRepository;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Character lifecycle operations that are not part of the core game loop:
 * creation (with seeded stats), retrieval, and cosmetic customization.
 */
@Service
public class CharacterService {

    /** Starter avatar catalog — cosmetic only, referenced by id from the frontend's picker. */
    public static final Set<String> AVATAR_CATALOG = Set.of(
            "wolf", "phoenix", "serpent", "golem", "raven", "tiger", "owl", "stag", "fox", "bear", "hawk", "turtle");

    private final CharacterRepository characterRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final CharacterAchievementRepository characterAchievementRepository;

    public CharacterService(CharacterRepository characterRepository, WorkoutLogRepository workoutLogRepository,
                             CharacterAchievementRepository characterAchievementRepository) {
        this.characterRepository = characterRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.characterAchievementRepository = characterAchievementRepository;
    }

    /**
     * Creates a level-1 character owned by {@code userId} and seeds all four
     * stats so quest claims have a target to apply XP to.
     */
    @Transactional
    public Character createCharacter(String characterName, UUID userId) {
        Character character = new Character(characterName);
        character.setUserId(userId);
        for (StatType type : StatType.values()) {
            character.addStat(new CharacterStat(type));
        }
        return characterRepository.save(character);
    }

    /** Admin operation: fetch any character regardless of owner. */
    @Transactional(readOnly = true)
    public Character getCharacter(UUID id) {
        return characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + id));
    }

    /**
     * Fetches a character only if it is owned by {@code userId}; otherwise
     * throws 404 (not 403) so ownership isn't leaked to unauthorized callers.
     */
    @Transactional(readOnly = true)
    public Character getOwnedCharacter(UUID id, UUID userId) {
        return characterRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Character> listCharacters() {
        return characterRepository.findAll();
    }

    /**
     * Fetches the calling player's own character without needing to know its
     * id up front — used by the login flow, which only has a user id.
     */
    @Transactional(readOnly = true)
    public Character getMyCharacter(UUID userId) {
        return characterRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No character found for this account"));
    }

    /**
     * Admin operation: directly overwrite a character's core fields.
     * Stats and workout logs are left untouched.
     */
    @Transactional
    public Character updateCharacter(UUID id, AdminCharacterUpdateRequest req) {
        Character character = characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + id));
        character.setCharacterName(req.getCharacterName());
        character.setCurrentLevel(req.getCurrentLevel());
        character.setOverallXp(req.getOverallXp());
        character.setStreakCount(req.getStreakCount());
        return characterRepository.save(character);
    }

    /**
     * Permanently deletes a character. Stats and workout logs are removed by the
     * {@code ON DELETE CASCADE} foreign keys defined in schema.sql.
     */
    @Transactional
    public void deleteCharacter(UUID id) {
        if (!characterRepository.existsById(id)) {
            throw new ResourceNotFoundException("Character not found: " + id);
        }
        characterRepository.deleteById(id);
    }

    /** Owner-scoped delete: 404s instead of deleting a character owned by someone else. */
    @Transactional
    public void deleteOwnedCharacter(UUID id, UUID userId) {
        Character character = getOwnedCharacter(id, userId);
        characterRepository.delete(character);
    }

    /**
     * Sets a character's cosmetic avatar and equipped title. {@code avatarId}
     * must be one of the starter catalog; {@code titleAchievementCode} must be
     * either blank/null (unequip) or the code of an achievement this specific
     * character has already unlocked — neither has any gameplay effect.
     */
    @Transactional
    public Character updateCustomization(UUID id, UUID userId, String avatarId, String titleAchievementCode) {
        Character character = getOwnedCharacter(id, userId);

        if (!AVATAR_CATALOG.contains(avatarId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown avatarId: " + avatarId);
        }
        character.setAvatarId(avatarId);

        String normalizedTitle = (titleAchievementCode == null || titleAchievementCode.isBlank())
                ? null : titleAchievementCode.trim();
        if (normalizedTitle != null
                && !characterAchievementRepository.existsByCharacterIdAndAchievementCode(id, normalizedTitle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Achievement not unlocked by this character: " + normalizedTitle);
        }
        character.setTitleAchievementCode(normalizedTitle);

        return characterRepository.save(character);
    }

    /**
     * The calling player's own claim history, most recent first — powers the
     * progress dashboard's XP-over-time and per-stat charts.
     */
    @Transactional(readOnly = true)
    public List<WorkoutLogEntryResponse> getOwnedWorkoutHistory(UUID id, UUID userId) {
        getOwnedCharacter(id, userId);
        return workoutLogRepository.findByCharacterIdOrderByLoggedAtDesc(id).stream()
                .map(WorkoutLogEntryResponse::from)
                .collect(Collectors.toList());
    }
}
