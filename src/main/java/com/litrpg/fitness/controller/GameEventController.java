package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.ActiveEventDTO;
import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.service.GameEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public, read-only view of currently-running seasonal events — powers the
 * quest-board banner. Full CRUD lives at {@code /api/admin/events}.
 */
@RestController
@RequestMapping("/api/events")
public class GameEventController {

    private final GameEventService gameEventService;

    public GameEventController(GameEventService gameEventService) {
        this.gameEventService = gameEventService;
    }

    /**
     * {@code GET /api/events/active} — every event running right now, optionally
     * narrowed to those applicable to one stat with {@code ?stat=CON}.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveEventDTO>> getActiveEvents(
            @RequestParam(name = "stat", required = false) StatType stat) {
        LocalDateTime now = LocalDateTime.now();
        List<ActiveEventDTO> events = gameEventService.listAll().stream()
                .filter(e -> e.isActiveAt(now))
                .filter(e -> stat == null || e.appliesTo(stat))
                .sorted(Comparator.comparing(GameEvent::getEndAt))
                .map(ActiveEventDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }
}
