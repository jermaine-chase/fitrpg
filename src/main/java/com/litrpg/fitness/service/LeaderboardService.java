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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ranks characters by level, lifetime XP, or streak, either across every
 * character on the instance or restricted to the caller's accepted friends.
 */
@Service
public class LeaderboardService {

    private final CharacterRepository characterRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public LeaderboardService(CharacterRepository characterRepository,
                               FriendshipRepository friendshipRepository,
                               UserRepository userRepository) {
        this.characterRepository = characterRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboard(UUID requesterId, LeaderboardScope scope,
                                                      LeaderboardMetric metric, int limit) {
        List<Character> candidates = scope == LeaderboardScope.FRIENDS
                ? friendsVisibleCharacters(requesterId)
                : characterRepository.findAll();

        List<Character> ranked = candidates.stream()
                .sorted(Comparator.comparingLong((Character c) -> metricValue(c, metric)).reversed())
                .limit(limit)
                .toList();

        List<LeaderboardEntryDTO> result = new ArrayList<>();
        int rank = 1;
        for (Character c : ranked) {
            result.add(new LeaderboardEntryDTO(rank++, c.getId(), c.getCharacterName(),
                    usernameOf(c.getUserId()), metricValue(c, metric)));
        }
        return result;
    }

    /**
     * The requester's own character (if any) plus every accepted friend's
     * character whose *friend-granted* visibility to the requester is
     * {@code BASIC} or {@code FULL} — mirrors the trimming rule used by
     * {@link FriendService#getActivityFeed}, so a friend that hides their
     * details from the activity feed is equally invisible on the leaderboard.
     */
    private List<Character> friendsVisibleCharacters(UUID requesterId) {
        List<Character> visible = new ArrayList<>();
        characterRepository.findFirstByUserId(requesterId).ifPresent(visible::add);

        for (Friendship f : friendshipRepository.findAcceptedForUser(requesterId)) {
            UUID otherUserId = f.otherUser(requesterId);
            User other = userRepository.findById(otherUserId).orElse(null);
            if (other == null) {
                continue;
            }
            FriendVisibility override = f.visibilityOverrideBy(otherUserId);
            FriendVisibility effective = override != null ? override : other.getDefaultFriendVisibility();
            if (effective == FriendVisibility.NONE) {
                continue;
            }
            characterRepository.findFirstByUserId(otherUserId).ifPresent(visible::add);
        }
        return visible;
    }

    private long metricValue(Character c, LeaderboardMetric metric) {
        return switch (metric) {
            case LEVEL -> c.getCurrentLevel();
            case XP -> lifetimeXp(c);
            case STREAK -> c.getStreakCount();
        };
    }

    /** Total XP ever earned: every completed level's threshold, plus progress in the current level. */
    private long lifetimeXp(Character c) {
        long total = c.getOverallXp();
        for (int lvl = 1; lvl < c.getCurrentLevel(); lvl++) {
            total += GameFormulas.xpForNextLevel(lvl);
        }
        return total;
    }

    private String usernameOf(UUID userId) {
        if (userId == null) {
            return "unknown";
        }
        return userRepository.findById(userId).map(User::getUsername).orElse("unknown");
    }
}
