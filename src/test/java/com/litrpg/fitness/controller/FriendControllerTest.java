package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.FriendRequestDTO;
import com.litrpg.fitness.dto.FriendshipResponse;
import com.litrpg.fitness.dto.VisibilityRequest;
import com.litrpg.fitness.model.FriendVisibility;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {

    @Mock
    private FriendService friendService;

    private FriendController controller;
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(userId, "hero");

    @BeforeEach
    void setUp() {
        controller = new FriendController(friendService);
    }

    @Test
    void listFriends_delegatesToService() {
        when(friendService.listFriends(userId)).thenReturn(List.of());
        assertThat(controller.listFriends(principal).getBody()).isEmpty();
    }

    @Test
    void getActivityFeed_delegatesToService() {
        when(friendService.getActivityFeed(userId)).thenReturn(List.of());
        assertThat(controller.getActivityFeed(principal).getBody()).isEmpty();
    }

    @Test
    void listIncomingRequests_delegatesToService() {
        when(friendService.listIncomingRequests(userId)).thenReturn(List.of());
        assertThat(controller.listIncomingRequests(principal).getBody()).isEmpty();
    }

    @Test
    void listOutgoingRequests_delegatesToService() {
        when(friendService.listOutgoingRequests(userId)).thenReturn(List.of());
        assertThat(controller.listOutgoingRequests(principal).getBody()).isEmpty();
    }

    @Test
    void sendRequest_returns201() {
        FriendRequestDTO request = new FriendRequestDTO();
        request.setUsername("target");
        FriendshipResponse expected = new FriendshipResponse();
        when(friendService.sendRequest(userId, "target")).thenReturn(expected);

        var response = controller.sendRequest(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void acceptRequest_respondsWithAccept() {
        UUID id = UUID.randomUUID();
        FriendshipResponse expected = new FriendshipResponse();
        when(friendService.respondToRequest(userId, id, true)).thenReturn(expected);

        assertThat(controller.acceptRequest(principal, id).getBody()).isSameAs(expected);
    }

    @Test
    void declineRequest_respondsWithDecline() {
        UUID id = UUID.randomUUID();
        FriendshipResponse expected = new FriendshipResponse();
        when(friendService.respondToRequest(userId, id, false)).thenReturn(expected);

        assertThat(controller.declineRequest(principal, id).getBody()).isSameAs(expected);
    }

    @Test
    void removeFriend_returnsNoContent() {
        UUID id = UUID.randomUUID();

        var response = controller.removeFriend(principal, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(friendService).removeFriend(userId, id);
    }

    @Test
    void getFriendDetail_delegatesToService() {
        UUID id = UUID.randomUUID();
        when(friendService.getFriendDetail(userId, id)).thenReturn(null);

        controller.getFriendDetail(principal, id);

        verify(friendService).getFriendDetail(userId, id);
    }

    @Test
    void getDefaultVisibility_delegatesToService() {
        when(friendService.getDefaultVisibility(userId)).thenReturn(null);

        controller.getDefaultVisibility(principal);

        verify(friendService).getDefaultVisibility(userId);
    }

    @Test
    void setDefaultVisibility_delegatesToService() {
        VisibilityRequest request = new VisibilityRequest();
        request.setVisibility(FriendVisibility.FULL);

        controller.setDefaultVisibility(principal, request);

        verify(friendService).setDefaultVisibility(userId, FriendVisibility.FULL);
    }

    @Test
    void setVisibilityForFriend_delegatesToService() {
        UUID id = UUID.randomUUID();
        VisibilityRequest request = new VisibilityRequest();
        request.setVisibility(FriendVisibility.NONE);

        controller.setVisibilityForFriend(principal, id, request);

        verify(friendService).setVisibilityForFriend(userId, id, FriendVisibility.NONE);
    }
}
