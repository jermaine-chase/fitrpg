package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records that a character has unlocked a given {@link Achievement}. Maps to
 * the {@code character_achievements} table; the unique constraint guarantees
 * a badge is only ever unlocked once per character.
 */
@Entity
@Table(
        name = "character_achievements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_character_achievement",
                columnNames = {"character_id", "achievement_code"}))
public class CharacterAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Column(name = "achievement_code", nullable = false)
    private String achievementCode;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    public CharacterAchievement() {
    }

    public CharacterAchievement(UUID characterId, String achievementCode) {
        this.characterId = characterId;
        this.achievementCode = achievementCode;
        this.unlockedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public String getAchievementCode() {
        return achievementCode;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }
}
