package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.LeaderboardEntryDTO;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.FriendVisibility;
import com.litrpg.fitness.model.Friendship;
import com.litrpg.fitness.model.LeaderboardMetric;
import com.litrpg.fitness.model.LeaderboardScope;
import com.litrpg.fitness.model.User;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.FriendshipRepository;
import com.litrpg.fitness.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserRepository userRepository;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(characterRepository, friendshipRepository, userRepository);
    }

    private Character character(String name, UUID userId, int level, int overallXp, int streak) {
        Character c = new Character(name);
        c.setId(UUID.randomUUID());
        c.setUserId(userId);
        c.setCurrentLevel(level);
        c.setOverallXp(overallXp);
        c.setStreakCount(streak);
        return c;
    }

    @Test
    void getLeaderboard_rankedByLevelDescending() {
        Character low = character("Low", UUID.randomUUID(), 2, 0, 0);
        Character high = character("High", UUID.randomUUID(), 10, 0, 0);
        when(characterRepository.findAll()).thenReturn(List.of(low, high));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        List<LeaderboardEntryDTO> result = leaderboardService.getLeaderboard(
                UUID.randomUUID(), LeaderboardScope.GLOBAL, LeaderboardMetric.LEVEL, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).characterName()).isEqualTo("High");
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(1).characterName()).isEqualTo("Low");
        assertThat(result.get(0).username()).isEqualTo("unknown");
    }

    @Test
    void getLeaderboard_limitsResults() {
        Character a = character("A", UUID.randomUUID(), 1, 0, 0);
        Character b = character("B", UUID.randomUUID(), 2, 0, 0);
        Character c = character("C", UUID.randomUUID(), 3, 0, 0);
        when(characterRepository.findAll()).thenReturn(List.of(a, b, c));

        List<LeaderboardEntryDTO> result = leaderboardService.getLeaderboard(
                UUID.randomUUID(), LeaderboardScope.GLOBAL, LeaderboardMetric.LEVEL, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void getLeaderboard_streakMetricUsesStreakCount() {
        Character a = character("A", UUID.randomUUID(), 5, 0, 1);
        Character b = character("B", UUID.randomUUID(), 1, 0, 9);
        when(characterRepository.findAll()).thenReturn(List.of(a, b));

        List<LeaderboardEntryDTO> result = leaderboardService.getLeaderboard(
                UUID.randomUUID(), LeaderboardScope.GLOBAL, LeaderboardMetric.STREAK, 10);

        assertThat(result.get(0).characterName()).isEqualTo("B");
        assertThat(result.get(0).value()).isEqualTo(9);
    }

    @Test
    void getLeaderboard_xpMetricIncludesLifetimeXpFromPastLevels() {
        Character a = character("A", UUID.randomUUID(), 2, 10, 0);
        when(characterRepository.findAll()).thenReturn(List.of(a));

        List<LeaderboardEntryDTO> result = leaderboardService.getLeaderboard(
                UUID.randomUUID(), LeaderboardScope.GLOBAL, LeaderboardMetric.XP, 10);

        long expected = GameFormulas.xpForNextLevel(1) + 10;
        assertThat(result.get(0).value()).isEqualTo(expected);
    }

    @Test
    void getLeaderboard_friendsScopeIncludesOwnAndVisibleFriendCharacters() {
        UUID requesterId = UUID.randomUUID();
        UUID friendUserId = UUID.randomUUID();
        UUID hiddenFriendUserId = UUID.randomUUID();
        Character ownCharacter = character("Me", requesterId, 5, 0, 0);
        Character friendCharacter = character("Friend", friendUserId, 3, 0, 0);

        when(characterRepository.findFirstByUserId(requesterId)).thenReturn(Optional.of(ownCharacter));

        Friendship visibleFriendship = new Friendship(requesterId, friendUserId);
        Friendship hiddenFriendship = new Friendship(requesterId, hiddenFriendUserId);
        when(friendshipRepository.findAcceptedForUser(requesterId))
                .thenReturn(List.of(visibleFriendship, hiddenFriendship));

        User friendUser = new User("friend", "hash");
        friendUser.setId(friendUserId);
        friendUser.setDefaultFriendVisibility(FriendVisibility.BASIC);
        User hiddenUser = new User("hidden", "hash");
        hiddenUser.setId(hiddenFriendUserId);
        hiddenUser.setDefaultFriendVisibility(FriendVisibility.NONE);

        when(userRepository.findById(friendUserId)).thenReturn(Optional.of(friendUser));
        when(userRepository.findById(hiddenFriendUserId)).thenReturn(Optional.of(hiddenUser));
        when(characterRepository.findFirstByUserId(friendUserId)).thenReturn(Optional.of(friendCharacter));

        List<LeaderboardEntryDTO> result = leaderboardService.getLeaderboard(
                requesterId, LeaderboardScope.FRIENDS, LeaderboardMetric.LEVEL, 10);

        assertThat(result).extracting(LeaderboardEntryDTO::characterName).containsExactly("Me", "Friend");
    }
}
