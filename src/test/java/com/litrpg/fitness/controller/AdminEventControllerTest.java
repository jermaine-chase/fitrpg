package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.GameEventRequest;
import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.service.GameEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEventControllerTest {

    @Mock
    private GameEventService gameEventService;

    private AdminEventController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminEventController(gameEventService);
    }

    private GameEvent event() {
        GameEvent e = new GameEvent();
        e.setId(UUID.randomUUID());
        e.setName("Event");
        e.setStartAt(LocalDateTime.now());
        e.setEndAt(LocalDateTime.now().plusDays(1));
        e.setXpMultiplier(new BigDecimal("1.50"));
        e.setAppliesToStat(StatType.STR);
        return e;
    }

    @Test
    void listEvents_mapsAllEventsToDto() {
        when(gameEventService.listAll()).thenReturn(List.of(event()));

        var response = controller.listEvents();

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void createEvent_returns201() {
        GameEventRequest request = new GameEventRequest();
        GameEvent created = event();
        when(gameEventService.create(request)).thenReturn(created);

        var response = controller.createEvent(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Event");
    }

    @Test
    void updateEvent_delegatesToService() {
        UUID id = UUID.randomUUID();
        GameEventRequest request = new GameEventRequest();
        GameEvent updated = event();
        when(gameEventService.update(id, request)).thenReturn(updated);

        var response = controller.updateEvent(id, request);

        assertThat(response.getBody().name()).isEqualTo("Event");
    }

    @Test
    void deleteEvent_returnsNoContent() {
        UUID id = UUID.randomUUID();

        var response = controller.deleteEvent(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(gameEventService).delete(id);
    }
}
