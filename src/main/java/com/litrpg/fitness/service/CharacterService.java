package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AdminCharacterUpdateRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Character lifecycle operations that are not part of the core game loop:
 * creation (with seeded stats) and retrieval.
 */
@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /**
     * Creates a level-1 character and seeds all four stats so quest claims have
     * a target to apply XP to.
     */
    @Transactional
    public Character createCharacter(String characterName) {
        Character character = new Character(characterName);
        for (StatType type : StatType.values()) {
            character.addStat(new CharacterStat(type));
        }
        return characterRepository.save(character);
    }

    @Transactional(readOnly = true)
    public Character getCharacter(UUID id) {
        return characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Character> listCharacters() {
        return characterRepository.findAll();
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
}
