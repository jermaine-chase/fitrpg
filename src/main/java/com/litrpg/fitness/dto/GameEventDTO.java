package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Full event definition, returned by the admin CRUD endpoints. */
public record GameEventDTO(UUID id, String name, LocalDateTime startAt, LocalDateTime endAt,
                            BigDecimal xpMultiplier, StatType appliesToStat) {

    public static GameEventDTO from(GameEvent e) {
        return new GameEventDTO(e.getId(), e.getName(), e.getStartAt(), e.getEndAt(),
                e.getXpMultiplier(), e.getAppliesToStat());
    }
}
