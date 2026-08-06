package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.GameEventRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.GameEvent;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.GameEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Time-boxed seasonal XP multipliers. Checked at claim time — the same
 * "is this currently true?" pattern {@link MidnightDecayService} uses for
 * inactivity — rather than a scheduler that flips characters in or out.
 */
@Service
public class GameEventService {

    private final GameEventRepository gameEventRepository;

    public GameEventService(GameEventRepository gameEventRepository) {
        this.gameEventRepository = gameEventRepository;
    }

    /** Events running right now whose bonus applies to the given stat (or every stat, if unrestricted). */
    @Transactional(readOnly = true)
    public List<GameEvent> listActiveEvents(StatType stat) {
        return gameEventRepository.findActive(LocalDateTime.now()).stream()
                .filter(e -> e.appliesTo(stat))
                .toList();
    }

    /**
     * The combined multiplier from every currently-active event applicable
     * to {@code stat}, stacked multiplicatively (1.0 if none are running).
     */
    @Transactional(readOnly = true)
    public double getActiveMultiplier(StatType stat) {
        double multiplier = 1.0;
        for (GameEvent e : listActiveEvents(stat)) {
            multiplier *= e.getXpMultiplier().doubleValue();
        }
        return multiplier;
    }

    @Transactional(readOnly = true)
    public List<GameEvent> listAll() {
        return gameEventRepository.findAll();
    }

    @Transactional
    public GameEvent create(GameEventRequest request) {
        GameEvent event = new GameEvent();
        return applyRequest(event, request);
    }

    @Transactional
    public GameEvent update(UUID id, GameEventRequest request) {
        GameEvent event = gameEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Game event not found: " + id));
        return applyRequest(event, request);
    }

    @Transactional
    public void delete(UUID id) {
        if (!gameEventRepository.existsById(id)) {
            throw new ResourceNotFoundException("Game event not found: " + id);
        }
        gameEventRepository.deleteById(id);
    }

    private GameEvent applyRequest(GameEvent event, GameEventRequest request) {
        event.setName(request.getName());
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setXpMultiplier(request.getXpMultiplier().setScale(2, RoundingMode.HALF_UP));
        event.setAppliesToStat(request.getAppliesToStat());
        return gameEventRepository.save(event);
    }
}
