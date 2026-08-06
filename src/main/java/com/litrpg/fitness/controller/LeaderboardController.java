package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.LeaderboardEntryDTO;
import com.litrpg.fitness.model.LeaderboardMetric;
import com.litrpg.fitness.model.LeaderboardScope;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ranks characters by level, lifetime XP, or streak. Requires a player JWT
 * (see {@link com.litrpg.fitness.config.SecurityConfig}) because the
 * {@code friends} scope needs to know who's asking.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final int MAX_LIMIT = 100;

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * {@code GET /api/leaderboard?scope=global|friends&metric=level|xp|streak&limit=20}
     * {@code scope} defaults to {@code global}, {@code metric} to {@code level}.
     */
    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "scope", defaultValue = "global") String scope,
            @RequestParam(name = "metric", defaultValue = "level") String metric,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        LeaderboardScope parsedScope = LeaderboardScope.valueOf(scope.trim().toUpperCase());
        LeaderboardMetric parsedMetric = LeaderboardMetric.valueOf(metric.trim().toUpperCase());
        int cappedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        return ResponseEntity.ok(
                leaderboardService.getLeaderboard(principal.getId(), parsedScope, parsedMetric, cappedLimit));
    }
}
