package com.litrpg.fitness.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The player's character sheet. Maps to the {@code characters} table.
 */
@Entity
@Table(name = "characters")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Owning player account. Null for characters created before player accounts existed. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "character_name", nullable = false)
    private String characterName;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "overall_xp", nullable = false)
    private int overallXp = 0;

    @Column(name = "streak_count", nullable = false)
    private int streakCount = 0;

    /** Grace tokens that preserve the streak on a missed day instead of halving it. */
    @Column(name = "streak_freeze_count", nullable = false)
    private int streakFreezeCount = 1;

    @Column(name = "last_workout_date")
    private LocalDate lastWorkoutDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Eagerly loaded because virtually every read of a character also needs its
     * stat block, and there are at most four rows per character.
     */
    @OneToMany(
            mappedBy = "character",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<CharacterStat> stats = new ArrayList<>();

    @OneToMany(
            mappedBy = "character",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<WorkoutLog> workoutLogs = new ArrayList<>();

    public Character() {
    }

    public Character(String characterName) {
        this.characterName = characterName;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Convenience helper that keeps both sides of the relationship in sync. */
    public void addStat(CharacterStat stat) {
        stats.add(stat);
        stat.setCharacter(this);
    }

    /** Convenience helper that keeps both sides of the relationship in sync. */
    public void addWorkoutLog(WorkoutLog logEntry) {
        workoutLogs.add(logEntry);
        logEntry.setCharacter(this);
    }

    // ---- getters / setters --------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public int getOverallXp() {
        return overallXp;
    }

    public void setOverallXp(int overallXp) {
        this.overallXp = overallXp;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public void setStreakCount(int streakCount) {
        this.streakCount = streakCount;
    }

    public int getStreakFreezeCount() {
        return streakFreezeCount;
    }

    public void setStreakFreezeCount(int streakFreezeCount) {
        this.streakFreezeCount = streakFreezeCount;
    }

    public LocalDate getLastWorkoutDate() {
        return lastWorkoutDate;
    }

    public void setLastWorkoutDate(LocalDate lastWorkoutDate) {
        this.lastWorkoutDate = lastWorkoutDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<CharacterStat> getStats() {
        return stats;
    }

    public void setStats(List<CharacterStat> stats) {
        this.stats = stats;
    }

    public List<WorkoutLog> getWorkoutLogs() {
        return workoutLogs;
    }

    public void setWorkoutLogs(List<WorkoutLog> workoutLogs) {
        this.workoutLogs = workoutLogs;
    }
}
