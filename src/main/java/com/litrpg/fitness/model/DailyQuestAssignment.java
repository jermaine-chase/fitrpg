package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The one quest highlighted as a character's "Daily Focus" for a given date.
 * Generated lazily on first read of the day (see {@code DailyQuestService})
 * rather than by a nightly batch job, so a brand-new character gets one
 * immediately instead of waiting for the next midnight run.
 */
@Entity
@Table(name = "daily_quest_assignments")
public class DailyQuestAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Column(name = "quest_id", nullable = false)
    private String questId;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    public DailyQuestAssignment() {
    }

    public DailyQuestAssignment(UUID characterId, String questId, LocalDate assignedDate) {
        this.characterId = characterId;
        this.questId = questId;
        this.assignedDate = assignedDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public String getQuestId() {
        return questId;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }
}
