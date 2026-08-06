package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.GameEventRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.GameEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEventServiceTest {

    @Mock
    private GameEventRepository gameEventRepository;

    private GameEventService gameEventService;

    @BeforeEach
    void setUp() {
        gameEventService = new GameEventService(gameEventRepository);
    }

    private GameEvent event(StatType stat, BigDecimal multiplier) {
        GameEvent event = new GameEvent();
        event.setName("Double XP Weekend");
        event.setStartAt(LocalDateTime.now().minusDays(1));
        event.setEndAt(LocalDateTime.now().plusDays(1));
        event.setXpMultiplier(multiplier);
        event.setAppliesToStat(stat);
        return event;
    }

    @Test
    void getActiveMultiplier_returnsOneWhenNoEventsActive() {
        when(gameEventRepository.findActive(any())).thenReturn(List.of());

        assertThat(gameEventService.getActiveMultiplier(StatType.STR)).isEqualTo(1.0);
    }

    @Test
    void getActiveMultiplier_appliesMatchingStatEvent() {
        when(gameEventRepository.findActive(any())).thenReturn(List.of(event(StatType.STR, new BigDecimal("2.00"))));

        assertThat(gameEventService.getActiveMultiplier(StatType.STR)).isEqualTo(2.0);
        assertThat(gameEventService.getActiveMultiplier(StatType.DEX)).isEqualTo(1.0);
    }

    @Test
    void getActiveMultiplier_stacksMultipleEventsMultiplicatively() {
        when(gameEventRepository.findActive(any())).thenReturn(List.of(
                event(null, new BigDecimal("2.00")),
                event(StatType.STR, new BigDecimal("1.50"))));

        assertThat(gameEventService.getActiveMultiplier(StatType.STR)).isEqualTo(3.0);
    }

    @Test
    void create_savesNewEventFromRequest() {
        GameEventRequest request = new GameEventRequest();
        request.setName("Winter Grind");
        request.setStartAt(LocalDateTime.now());
        request.setEndAt(LocalDateTime.now().plusDays(7));
        request.setXpMultiplier(new BigDecimal("1.5"));
        request.setAppliesToStat(StatType.CON);
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        GameEvent created = gameEventService.create(request);

        assertThat(created.getName()).isEqualTo("Winter Grind");
        assertThat(created.getXpMultiplier()).isEqualByComparingTo("1.50");
        assertThat(created.getAppliesToStat()).isEqualTo(StatType.CON);
    }

    @Test
    void update_throwsWhenEventMissing() {
        UUID id = UUID.randomUUID();
        when(gameEventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameEventService.update(id, new GameEventRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_overwritesExistingEvent() {
        UUID id = UUID.randomUUID();
        GameEvent existing = event(StatType.STR, new BigDecimal("1.0"));
        when(gameEventRepository.findById(id)).thenReturn(Optional.of(existing));
        when(gameEventRepository.save(existing)).thenReturn(existing);

        GameEventRequest request = new GameEventRequest();
        request.setName("Updated");
        request.setStartAt(LocalDateTime.now());
        request.setEndAt(LocalDateTime.now().plusDays(1));
        request.setXpMultiplier(new BigDecimal("3"));
        request.setAppliesToStat(StatType.WIL);

        GameEvent updated = gameEventService.update(id, request);

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getAppliesToStat()).isEqualTo(StatType.WIL);
    }

    @Test
    void delete_throwsWhenEventMissing() {
        UUID id = UUID.randomUUID();
        when(gameEventRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> gameEventService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(gameEventRepository, never()).deleteById(any());
    }

    @Test
    void delete_deletesWhenPresent() {
        UUID id = UUID.randomUUID();
        when(gameEventRepository.existsById(id)).thenReturn(true);

        gameEventService.delete(id);

        verify(gameEventRepository).deleteById(id);
    }

    @Test
    void listAll_delegatesToRepository() {
        List<GameEvent> events = List.of(event(StatType.STR, BigDecimal.ONE));
        when(gameEventRepository.findAll()).thenReturn(events);

        assertThat(gameEventService.listAll()).isEqualTo(events);
    }

    @Test
    void listActiveEvents_filtersByStat() {
        GameEvent strOnly = event(StatType.STR, BigDecimal.ONE);
        GameEvent allStats = event(null, BigDecimal.ONE);
        when(gameEventRepository.findActive(any())).thenReturn(List.of(strOnly, allStats));

        assertThat(gameEventService.listActiveEvents(StatType.DEX)).containsExactly(allStats);
    }
}
