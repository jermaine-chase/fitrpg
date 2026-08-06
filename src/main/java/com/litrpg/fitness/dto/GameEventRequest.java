package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.StatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request body for admin game-event create/update operations. */
public class GameEventRequest {

    @NotBlank
    private String name;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    @NotNull
    @Positive
    private BigDecimal xpMultiplier;

    /** Null applies the bonus to every stat. */
    private StatType appliesToStat;

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
}
