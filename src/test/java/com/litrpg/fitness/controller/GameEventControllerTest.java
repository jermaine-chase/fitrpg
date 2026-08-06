package com.litrpg.fitness.controller;

import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.service.GameEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEventControllerTest {

    @Mock
    private GameEventService gameEventService;

    private GameEventController controller;

    @BeforeEach
    void setUp() {
        controller = new GameEventController(gameEventService);
    }

    private GameEvent event(String name, StatType stat, LocalDateTime start, LocalDateTime end) {
        GameEvent e = new GameEvent();
        e.setName(name);
        e.setStartAt(start);
        e.setEndAt(end);
        e.setXpMultiplier(new BigDecimal("2.00"));
        e.setAppliesToStat(stat);
        return e;
    }

    @Test
    void getActiveEvents_filtersToCurrentlyRunningAndMatchingStat() {
        LocalDateTime now = LocalDateTime.now();
        GameEvent active = event("Active", StatType.STR, now.minusDays(1), now.plusDays(1));
        GameEvent expired = event("Expired", StatType.STR, now.minusDays(10), now.minusDays(5));
        GameEvent wrongStat = event("Other", StatType.DEX, now.minusDays(1), now.plusDays(1));
        when(gameEventService.listAll()).thenReturn(List.of(active, expired, wrongStat));

        var response = controller.getActiveEvents(StatType.STR);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("Active");
    }

    @Test
    void getActiveEvents_noStatFilterReturnsAllActive() {
        LocalDateTime now = LocalDateTime.now();
        GameEvent active = event("Active", null, now.minusDays(1), now.plusDays(1));
        when(gameEventService.listAll()).thenReturn(List.of(active));

        var response = controller.getActiveEvents(null);

        assertThat(response.getBody()).hasSize(1);
    }
}
