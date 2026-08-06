package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An immutable, append-only record of a completed quest. Maps to the
 * {@code workout_logs} table. Once written, rows are never updated.
 */
@Entity
@Table(name = "workout_logs")
public class WorkoutLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(name = "quest_id")
    private String questId;

    @Column(name = "quest_title")
    private String questTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type", length = 3)
    private StatType statType;

    @Column(name = "base_xp_earned")
    private int baseXpEarned;

    @Column(name = "multiplier_applied")
    private BigDecimal multiplierApplied;

    @Column(name = "final_xp_awarded")
    private int finalXpAwarded;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    public WorkoutLog() {
    }

    @PrePersist
    protected void onCreate() {
        if (loggedAt == null) {
            loggedAt = LocalDateTime.now();
        }
    }

    // ---- getters / setters --------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }

    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

    public String getQuestTitle() {
        return questTitle;
    }

    public void setQuestTitle(String questTitle) {
        this.questTitle = questTitle;
    }

    public StatType getStatType() {
        return statType;
    }

    public void setStatType(StatType statType) {
        this.statType = statType;
    }

    public int getBaseXpEarned() {
        return baseXpEarned;
    }

    public void setBaseXpEarned(int baseXpEarned) {
        this.baseXpEarned = baseXpEarned;
    }

    public BigDecimal getMultiplierApplied() {
        return multiplierApplied;
    }

    public void setMultiplierApplied(BigDecimal multiplierApplied) {
        this.multiplierApplied = multiplierApplied;
    }

    public int getFinalXpAwarded() {
        return finalXpAwarded;
    }

    public void setFinalXpAwarded(int finalXpAwarded) {
        this.finalXpAwarded = finalXpAwarded;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(LocalDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }
}
