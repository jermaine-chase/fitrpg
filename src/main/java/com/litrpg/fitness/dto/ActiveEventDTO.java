package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trimmed, public view of a currently-running event — just enough for the
 * quest-board banner. Returned by {@code GET /api/events/active}.
 */
public record ActiveEventDTO(String name, BigDecimal xpMultiplier, StatType appliesToStat, LocalDateTime endAt) {

    public static ActiveEventDTO from(GameEvent e) {
        return new ActiveEventDTO(e.getName(), e.getXpMultiplier(), e.getAppliesToStat(), e.getEndAt());
    }
}
