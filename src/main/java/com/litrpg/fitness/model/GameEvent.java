package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A time-boxed seasonal XP multiplier. Checked at claim time, the same
 * pattern {@code MidnightDecayService} uses for decay — there is no
 * scheduler that flips characters in or out of an event; {@code GameEngineService}
 * simply asks whether one is active "right now" whenever a quest is claimed.
 */
@Entity
@Table(name = "game_events")
public class GameEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "xp_multiplier", nullable = false)
    private BigDecimal xpMultiplier;

    /** Null applies to every stat; otherwise the bonus only stacks on quests targeting this stat. */
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to_stat", length = 3)
    private StatType appliesToStat;

    public GameEvent() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public BigDecimal getXpMultiplier() {
        return xpMultiplier;
    }

    public void setXpMultiplier(BigDecimal xpMultiplier) {
        this.xpMultiplier = xpMultiplier;
    }

    public StatType getAppliesToStat() {
        return appliesToStat;
    }

    public void setAppliesToStat(StatType appliesToStat) {
        this.appliesToStat = appliesToStat;
    }

    /** Whether this event is running at the given instant. */
    public boolean isActiveAt(LocalDateTime instant) {
        return !instant.isBefore(startAt) && !instant.isAfter(endAt);
    }

    /** Whether this event's bonus applies to the given stat (null on the event means "all stats"). */
    public boolean appliesTo(StatType stat) {
        return appliesToStat == null || appliesToStat == stat;
    }
}
