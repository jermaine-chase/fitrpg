package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.FriendActivityEntry;
import com.litrpg.fitness.dto.FriendCharacterSummary;
import com.litrpg.fitness.dto.FriendDetailResponse;
import com.litrpg.fitness.dto.FriendSettingsResponse;
import com.litrpg.fitness.dto.FriendshipResponse;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.FriendVisibility;
import com.litrpg.fitness.model.Friendship;
import com.litrpg.fitness.model.FriendshipStatus;
import com.litrpg.fitness.model.User;
import com.litrpg.fitness.model.WorkoutLog;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.FriendshipRepository;
import com.litrpg.fitness.repository.UserRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Friend requests, friendships, and per-friend visibility controls between
 * player accounts. Each user has a {@code defaultFriendVisibility} applied to
 * every friend, which a per-friendship override (set by either side, only
 * affecting what *they* reveal) can supersede.
 */
@Service
public class FriendService {

    private static final int ACTIVITY_FEED_LOOKBACK_DAYS = 14;

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final WorkoutLogRepository workoutLogRepository;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository,
                          CharacterRepository characterRepository, WorkoutLogRepository workoutLogRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.workoutLogRepository = workoutLogRepository;
    }

    /** Sends a friend request from {@code requesterId} to the user named {@code targetUsername}. */
    @Transactional
    public FriendshipResponse sendRequest(UUID requesterId, String targetUsername) {
        User target = userRepository.findByUsernameIgnoreCase(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUsername));
        if (target.getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot friend yourself");
        }

        Friendship existing = friendshipRepository.findBetween(requesterId, target.getId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == FriendshipStatus.DECLINED) {
                friendshipRepository.delete(existing);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        existing.getStatus() == FriendshipStatus.ACCEPTED
                                ? "Already friends with " + targetUsername
                                : "A friend request with " + targetUsername + " is already pending");
            }
        }

        Friendship friendship = friendshipRepository.save(new Friendship(requesterId, target.getId()));
        return FriendshipResponse.from(friendship, requesterId, targetUsername);
    }

    /** Accepts or declines a pending request addressed to {@code userId}. */
    @Transactional
    public FriendshipResponse respondToRequest(UUID userId, UUID friendshipId, boolean accept) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found: " + friendshipId));
        if (!friendship.getAddresseeId().equals(userId)) {
            // 404 rather than 403 so the request's existence isn't leaked to non-recipients.
            throw new ResourceNotFoundException("Friend request not found: " + friendshipId);
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already resolved");
        }

        friendship.setStatus(accept ? FriendshipStatus.ACCEPTED : FriendshipStatus.DECLINED);
        friendship.setRespondedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        String otherUsername = usernameOf(friendship.otherUser(userId));
        return FriendshipResponse.from(friendship, userId, otherUsername);
    }

    /** Removes an accepted friendship (either party may do this). */
    @Transactional
    public void removeFriend(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found: " + friendshipId));
        if (!friendship.getRequesterId().equals(userId) && !friendship.getAddresseeId().equals(userId)) {
            throw new ResourceNotFoundException("Friendship not found: " + friendshipId);
        }
        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> listFriends(UUID userId) {
        FriendVisibility myDefault = userRepository.findById(userId)
                .map(User::getDefaultFriendVisibility)
                .orElse(FriendVisibility.BASIC);
        return friendshipRepository.findAcceptedForUser(userId).stream()
                .map(f -> {
                    FriendVisibility granted = f.visibilityOverrideBy(userId) != null
                            ? f.visibilityOverrideBy(userId) : myDefault;
                    return FriendshipResponse.withVisibility(f, userId, usernameOf(f.otherUser(userId)), granted);
                })
                .collect(Collectors.toList());
    }

    /** The caller's default visibility, applied to friends without a per-friendship override. */
    @Transactional(readOnly = true)
    public FriendSettingsResponse getDefaultVisibility(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return new FriendSettingsResponse(user.getDefaultFriendVisibility());
    }

    @Transactional
    public FriendSettingsResponse setDefaultVisibility(UUID userId, FriendVisibility visibility) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setDefaultFriendVisibility(visibility);
        userRepository.save(user);
        return new FriendSettingsResponse(visibility);
    }

    /** Overrides what the caller reveals to one specific accepted friend. */
    @Transactional
    public FriendshipResponse setVisibilityForFriend(UUID userId, UUID friendshipId, FriendVisibility visibility) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found: " + friendshipId));
        if (!friendship.getRequesterId().equals(userId) && !friendship.getAddresseeId().equals(userId)) {
            throw new ResourceNotFoundException("Friendship not found: " + friendshipId);
        }
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not yet friends");
        }
        friendship.setVisibilityOverrideBy(userId, visibility);
        friendshipRepository.save(friendship);
        return FriendshipResponse.withVisibility(friendship, userId, usernameOf(friendship.otherUser(userId)), visibility);
    }

    /**
     * A friend's profile, trimmed to the visibility level *they* have granted
     * the caller (their per-friendship override, else their account default).
     */
    @Transactional(readOnly = true)
    public FriendDetailResponse getFriendDetail(UUID viewerId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found: " + friendshipId));
        if (!friendship.getRequesterId().equals(viewerId) && !friendship.getAddresseeId().equals(viewerId)) {
            throw new ResourceNotFoundException("Friendship not found: " + friendshipId);
        }
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not yet friends");
        }

        UUID otherUserId = friendship.otherUser(viewerId);
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + otherUserId));

        FriendVisibility override = friendship.visibilityOverrideBy(otherUserId);
        FriendVisibility effective = override != null ? override : other.getDefaultFriendVisibility();

        FriendCharacterSummary summary = null;
        if (effective != FriendVisibility.NONE) {
            Character character = characterRepository.findFirstByUserId(otherUserId).orElse(null);
            if (character != null) {
                summary = effective == FriendVisibility.FULL
                        ? FriendCharacterSummary.full(character)
                        : FriendCharacterSummary.basic(character);
            }
        }

        return new FriendDetailResponse(friendship.getId(), otherUserId, other.getUsername(), effective, summary);
    }

    /**
     * Recent quest claims from the caller's accepted friends, most recent
     * first, trimmed per-friend to the visibility level *they* have granted
     * the caller — friends at {@code NONE} are omitted entirely, {@code BASIC}
     * shows only that a claim happened, {@code FULL} shows quest/stat/XP.
     */
    @Transactional(readOnly = true)
    public List<FriendActivityEntry> getActivityFeed(UUID userId) {
        List<Friendship> friendships = friendshipRepository.findAcceptedForUser(userId);

        record FriendContext(UUID friendshipId, String username, FriendVisibility visibility) {
        }
        Map<UUID, FriendContext> contextByCharacterId = new HashMap<>();

        for (Friendship f : friendships) {
            UUID otherUserId = f.otherUser(userId);
            User other = userRepository.findById(otherUserId).orElse(null);
            if (other == null) {
                continue;
            }
            FriendVisibility override = f.visibilityOverrideBy(otherUserId);
            FriendVisibility effective = override != null ? override : other.getDefaultFriendVisibility();
            if (effective == FriendVisibility.NONE) {
                continue;
            }
            Character character = characterRepository.findFirstByUserId(otherUserId).orElse(null);
            if (character == null) {
                continue;
            }
            contextByCharacterId.put(character.getId(), new FriendContext(f.getId(), other.getUsername(), effective));
        }

        if (contextByCharacterId.isEmpty()) {
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_FEED_LOOKBACK_DAYS);
        List<WorkoutLog> logs = workoutLogRepository.findTop50ByCharacterIdInAndLoggedAtAfterOrderByLoggedAtDesc(
                List.copyOf(contextByCharacterId.keySet()), since);

        return logs.stream()
                .map(log -> {
                    FriendContext ctx = contextByCharacterId.get(log.getCharacter().getId());
                    String characterName = log.getCharacter().getCharacterName();
                    return ctx.visibility() == FriendVisibility.FULL
                            ? FriendActivityEntry.full(ctx.friendshipId(), ctx.username(), characterName, log)
                            : FriendActivityEntry.basic(ctx.friendshipId(), ctx.username(), characterName, log);
                })
                .collect(Collectors.toList());
    }

    /** Pending requests sent to {@code userId} by other users, awaiting a response. */
    @Transactional(readOnly = true)
    public List<FriendshipResponse> listIncomingRequests(UUID userId) {
        return friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> FriendshipResponse.from(f, userId, usernameOf(f.getRequesterId())))
                .collect(Collectors.toList());
    }

    /** Pending requests {@code userId} has sent, awaiting the other party's response. */
    @Transactional(readOnly = true)
    public List<FriendshipResponse> listOutgoingRequests(UUID userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> FriendshipResponse.from(f, userId, usernameOf(f.getAddresseeId())))
                .collect(Collectors.toList());
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("unknown");
    }
}
