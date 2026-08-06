package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.LeaderboardEntryDTO;
import com.litrpg.fitness.model.LeaderboardMetric;
import com.litrpg.fitness.model.LeaderboardScope;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.LeaderboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock
    private LeaderboardService leaderboardService;

    private LeaderboardController controller;

    @BeforeEach
    void setUp() {
        controller = new LeaderboardController(leaderboardService);
    }

    @Test
    void getLeaderboard_parsesScopeAndMetricAndCapsLimit() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        List<LeaderboardEntryDTO> expected = List.of(
                new LeaderboardEntryDTO(1, UUID.randomUUID(), "Hero", "hero", 10));
        when(leaderboardService.getLeaderboard(userId, LeaderboardScope.FRIENDS, LeaderboardMetric.XP, 100))
                .thenReturn(expected);

        var response = controller.getLeaderboard(principal, "friends", "xp", 500);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void getLeaderboard_defaultsAndMinimumLimit() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        when(leaderboardService.getLeaderboard(userId, LeaderboardScope.GLOBAL, LeaderboardMetric.LEVEL, 1))
                .thenReturn(List.of());

        var response = controller.getLeaderboard(principal, "GLOBAL", "LEVEL", -5);

        assertThat(response.getBody()).isEmpty();
    }
}
