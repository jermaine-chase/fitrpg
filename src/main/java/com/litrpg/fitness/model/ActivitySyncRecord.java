package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An immutable, raw record of one activity-sync submission — kept for
 * audit/debugging regardless of whether it crossed the XP thresholds. The
 * unique constraint on (character_id, source, activity_date) is what
 * prevents the same day's data from the same source crediting XP twice;
 * see {@code ActivitySyncService}.
 *
 * <p>{@code source} is a free-text label ("manual", "fitbit", "google_fit",
 * "health_connect", ...) — no OAuth/device integration exists yet, so today
 * every source is effectively "manual". Wiring a real wearable API is a
 * documented follow-up, not part of this groundwork.
 */
@Entity
@Table(
        name = "activity_sync_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_activity_sync_record",
                columnNames = {"character_id", "source", "activity_date"}))
public class ActivitySyncRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private int steps;

    @Column(name = "active_minutes", nullable = false)
    private int activeMinutes;

    @Column(name = "xp_awarded", nullable = false)
    private int xpAwarded;

    @Column(name = "synced_at", nullable = false, updatable = false)
    private LocalDateTime syncedAt;

    public ActivitySyncRecord() {
    }

    public ActivitySyncRecord(UUID characterId, String source, LocalDate activityDate, int steps, int activeMinutes) {
        this.characterId = characterId;
        this.source = source;
        this.activityDate = activityDate;
        this.steps = steps;
        this.activeMinutes = activeMinutes;
    }

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) {
            syncedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public int getSteps() {
        return steps;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public int getXpAwarded() {
        return xpAwarded;
    }

    public void setXpAwarded(int xpAwarded) {
        this.xpAwarded = xpAwarded;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }
}
