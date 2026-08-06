package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.service.GameFormulas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full character sheet returned by the read and claim endpoints. Built as an
 * explicit DTO (rather than serializing the entity directly) to avoid
 * bidirectional-relationship recursion and to expose derived fields like
 * {@code xpForNextLevel}.
 */
public class CharacterSheetResponse {

    private UUID id;
    private String characterName;
    private int currentLevel;
    private int overallXp;
    private int xpForNextLevel;
    private int streakCount;
    private int streakFreezesAvailable;
    private LocalDate lastWorkoutDate;
    private LocalDateTime createdAt;
    private List<StatResponse> stats;

    public static CharacterSheetResponse from(Character character) {
        CharacterSheetResponse r = new CharacterSheetResponse();
        r.id = character.getId();
        r.characterName = character.getCharacterName();
        r.currentLevel = character.getCurrentLevel();
        r.overallXp = character.getOverallXp();
        r.xpForNextLevel = GameFormulas.xpForNextLevel(character.getCurrentLevel());
        r.streakCount = character.getStreakCount();
        r.streakFreezesAvailable = character.getStreakFreezeCount();
        r.lastWorkoutDate = character.getLastWorkoutDate();
        r.createdAt = character.getCreatedAt();
        r.stats = character.getStats().stream()
                .sorted(Comparator.comparing(s -> s.getStatType().name()))
                .map(StatResponse::from)
                .collect(Collectors.toList());
        return r;
    }

    public UUID getId() {
        return id;
    }

    public String getCharacterName() {
        return characterName;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getOverallXp() {
        return overallXp;
    }

    public int getXpForNextLevel() {
        return xpForNextLevel;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public int getStreakFreezesAvailable() {
        return streakFreezesAvailable;
    }

    public LocalDate getLastWorkoutDate() {
        return lastWorkoutDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<StatResponse> getStats() {
        return stats;
    }
}
