package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.GameEventDTO;
import com.litrpg.fitness.dto.GameEventRequest;
import com.litrpg.fitness.service.GameEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only CRUD for timed seasonal XP-multiplier events. Requires the
 * admin role, enforced by {@link com.litrpg.fitness.config.SecurityConfig}
 * (`/api/admin/**`).
 */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final GameEventService gameEventService;

    public AdminEventController(GameEventService gameEventService) {
        this.gameEventService = gameEventService;
    }

    /** {@code GET /api/admin/events} — every event, past/active/future. */
    @GetMapping
    public ResponseEntity<List<GameEventDTO>> listEvents() {
        List<GameEventDTO> events = gameEventService.listAll().stream()
                .map(GameEventDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }

    /** {@code POST /api/admin/events} */
    @PostMapping
    public ResponseEntity<GameEventDTO> createEvent(@Valid @RequestBody GameEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GameEventDTO.from(gameEventService.create(request)));
    }

    /** {@code PUT /api/admin/events/{id}} */
    @PutMapping("/{id}")
    public ResponseEntity<GameEventDTO> updateEvent(@PathVariable UUID id, @Valid @RequestBody GameEventRequest request) {
        return ResponseEntity.ok(GameEventDTO.from(gameEventService.update(id, request)));
    }

    /** {@code DELETE /api/admin/events/{id}} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        gameEventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
