package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.FriendActivityEntry;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private WorkoutLogRepository workoutLogRepository;

    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(friendshipRepository, userRepository, characterRepository, workoutLogRepository);
    }

    private User user(String username) {
        User u = new User(username, "hash");
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void sendRequest_createsNewFriendship() {
        UUID requesterId = UUID.randomUUID();
        User target = user("target");
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findBetween(requesterId, target.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendshipResponse response = friendService.sendRequest(requesterId, "target");

        assertThat(response.getUsername()).isEqualTo("target");
        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void sendRequest_rejectsSelfFriending() {
        UUID requesterId = UUID.randomUUID();
        User self = user("me");
        self.setId(requesterId);
        when(userRepository.findByUsernameIgnoreCase("me")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> friendService.sendRequest(requesterId, "me"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot friend yourself");
    }

    @Test
    void sendRequest_rejectsWhenAlreadyFriends() {
        UUID requesterId = UUID.randomUUID();
        User target = user("target");
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));
        Friendship existing = new Friendship(requesterId, target.getId());
        existing.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findBetween(requesterId, target.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendService.sendRequest(requesterId, "target"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Already friends");
    }

    @Test
    void sendRequest_rejectsWhenPendingRequestExists() {
        UUID requesterId = UUID.randomUUID();
        User target = user("target");
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));
        Friendship existing = new Friendship(requesterId, target.getId());
        existing.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findBetween(requesterId, target.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendService.sendRequest(requesterId, "target"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void sendRequest_reReqestsAfterDecline() {
        UUID requesterId = UUID.randomUUID();
        User target = user("target");
        when(userRepository.findByUsernameIgnoreCase("target")).thenReturn(Optional.of(target));
        Friendship declined = new Friendship(requesterId, target.getId());
        declined.setStatus(FriendshipStatus.DECLINED);
        when(friendshipRepository.findBetween(requesterId, target.getId())).thenReturn(Optional.of(declined));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));

        FriendshipResponse response = friendService.sendRequest(requesterId, "target");

        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void sendRequest_throwsWhenTargetUserMissing() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.sendRequest(UUID.randomUUID(), "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void respondToRequest_acceptsPendingRequest() {
        UUID addresseeId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(requesterId, addresseeId);
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(friendship);

        FriendshipResponse response = friendService.respondToRequest(addresseeId, friendshipId, true);

        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getRespondedAt()).isNotNull();
    }

    @Test
    void respondToRequest_hides404WhenNotAddressee() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(requesterId, addresseeId);
        friendship.setId(friendshipId);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.respondToRequest(UUID.randomUUID(), friendshipId, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void respondToRequest_rejectsAlreadyResolvedRequest() {
        UUID addresseeId = UUID.randomUUID();
        Friendship friendship = new Friendship(UUID.randomUUID(), addresseeId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        UUID friendshipId = UUID.randomUUID();
        friendship.setId(friendshipId);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.respondToRequest(addresseeId, friendshipId, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    void removeFriend_removesWhenRequesterOrAddressee() {
        UUID userId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, UUID.randomUUID());
        friendship.setId(friendshipId);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendService.removeFriend(userId, friendshipId);
    }

    @Test
    void removeFriend_throwsWhenUserNotParty() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(UUID.randomUUID(), UUID.randomUUID());
        friendship.setId(friendshipId);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.removeFriend(UUID.randomUUID(), friendshipId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listFriends_usesPerFriendOverrideWhenSet() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        User me = user("me");
        me.setId(userId);
        me.setDefaultFriendVisibility(FriendVisibility.BASIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(me));

        Friendship friendship = new Friendship(userId, otherId);
        friendship.setVisibilityOverrideBy(userId, FriendVisibility.FULL);
        when(friendshipRepository.findAcceptedForUser(userId)).thenReturn(List.of(friendship));
        when(userRepository.findById(otherId)).thenReturn(Optional.of(user("other")));

        List<FriendshipResponse> result = friendService.listFriends(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVisibilityGranted()).isEqualTo(FriendVisibility.FULL);
    }

    @Test
    void getDefaultVisibility_returnsUsersSetting() {
        UUID userId = UUID.randomUUID();
        User u = user("me");
        u.setDefaultFriendVisibility(FriendVisibility.FULL);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        FriendSettingsResponse response = friendService.getDefaultVisibility(userId);

        assertThat(response.getDefaultVisibility()).isEqualTo(FriendVisibility.FULL);
    }

    @Test
    void setDefaultVisibility_updatesAndSavesUser() {
        UUID userId = UUID.randomUUID();
        User u = user("me");
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        FriendSettingsResponse response = friendService.setDefaultVisibility(userId, FriendVisibility.NONE);

        assertThat(response.getDefaultVisibility()).isEqualTo(FriendVisibility.NONE);
        assertThat(u.getDefaultFriendVisibility()).isEqualTo(FriendVisibility.NONE);
    }

    @Test
    void setVisibilityForFriend_rejectsWhenNotYetFriends() {
        UUID userId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, UUID.randomUUID());
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.setVisibilityForFriend(userId, friendshipId, FriendVisibility.FULL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not yet friends");
    }

    @Test
    void setVisibilityForFriend_setsOverride() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, otherId);
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(friendship);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(user("other")));

        FriendshipResponse response = friendService.setVisibilityForFriend(userId, friendshipId, FriendVisibility.NONE);

        assertThat(response.getVisibilityGranted()).isEqualTo(FriendVisibility.NONE);
        assertThat(friendship.getRequesterVisibility()).isEqualTo(FriendVisibility.NONE);
    }

    @Test
    void getFriendDetail_returnsNullCharacterWhenVisibilityNone() {
        UUID viewerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(viewerId, otherId);
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        User other = user("other");
        other.setId(otherId);
        other.setDefaultFriendVisibility(FriendVisibility.NONE);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));

        FriendDetailResponse response = friendService.getFriendDetail(viewerId, friendshipId);

        assertThat(response.getCharacter()).isNull();
        assertThat(response.getVisibility()).isEqualTo(FriendVisibility.NONE);
    }

    @Test
    void getFriendDetail_returnsFullSummaryWhenVisibilityFull() {
        UUID viewerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(viewerId, otherId);
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        User other = user("other");
        other.setId(otherId);
        other.setDefaultFriendVisibility(FriendVisibility.FULL);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));
        Character character = new Character("Buddy");
        when(characterRepository.findFirstByUserId(otherId)).thenReturn(Optional.of(character));

        FriendDetailResponse response = friendService.getFriendDetail(viewerId, friendshipId);

        assertThat(response.getCharacter()).isNotNull();
        assertThat(response.getCharacter().getCharacterName()).isEqualTo("Buddy");
        assertThat(response.getCharacter().getOverallXp()).isNotNull();
    }

    @Test
    void getFriendDetail_rejectsWhenNotYetFriends() {
        UUID viewerId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(viewerId, UUID.randomUUID());
        friendship.setId(friendshipId);
        friendship.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.getFriendDetail(viewerId, friendshipId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getActivityFeed_excludesFriendsWithNoneVisibility() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, otherId);
        when(friendshipRepository.findAcceptedForUser(userId)).thenReturn(List.of(friendship));
        User other = user("other");
        other.setId(otherId);
        other.setDefaultFriendVisibility(FriendVisibility.NONE);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));

        List<FriendActivityEntry> feed = friendService.getActivityFeed(userId);

        assertThat(feed).isEmpty();
    }

    @Test
    void getActivityFeed_returnsBasicEntriesForBasicVisibility() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, otherId);
        friendship.setId(friendshipId);
        when(friendshipRepository.findAcceptedForUser(userId)).thenReturn(List.of(friendship));
        User other = user("other");
        other.setId(otherId);
        other.setDefaultFriendVisibility(FriendVisibility.BASIC);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));
        Character character = new Character("Buddy");
        character.setId(UUID.randomUUID());
        when(characterRepository.findFirstByUserId(otherId)).thenReturn(Optional.of(character));

        WorkoutLog log = new WorkoutLog();
        log.setCharacter(character);
        log.setQuestTitle("Push-ups");
        log.setLoggedAt(LocalDateTime.now());
        when(workoutLogRepository.findTop50ByCharacterIdInAndLoggedAtAfterOrderByLoggedAtDesc(any(), any()))
                .thenReturn(List.of(log));

        List<FriendActivityEntry> feed = friendService.getActivityFeed(userId);

        assertThat(feed).hasSize(1);
        assertThat(feed.get(0).getCharacterName()).isEqualTo("Buddy");
        assertThat(feed.get(0).getQuestTitle()).isNull();
    }

    @Test
    void listIncomingRequests_returnsPendingRequestsAddressedToUser() {
        UUID userId = UUID.randomUUID();
        Friendship friendship = new Friendship(UUID.randomUUID(), userId);
        when(friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING))
                .thenReturn(List.of(friendship));

        List<FriendshipResponse> result = friendService.listIncomingRequests(userId);

        assertThat(result).hasSize(1);
    }

    @Test
    void listOutgoingRequests_returnsPendingRequestsSentByUser() {
        UUID userId = UUID.randomUUID();
        Friendship friendship = new Friendship(userId, UUID.randomUUID());
        when(friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING))
                .thenReturn(List.of(friendship));

        List<FriendshipResponse> result = friendService.listOutgoingRequests(userId);

        assertThat(result).hasSize(1);
    }
}
