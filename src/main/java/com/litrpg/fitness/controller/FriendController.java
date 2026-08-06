package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.FriendActivityEntry;
import com.litrpg.fitness.dto.FriendDetailResponse;
import com.litrpg.fitness.dto.FriendRequestDTO;
import com.litrpg.fitness.dto.FriendSettingsResponse;
import com.litrpg.fitness.dto.FriendshipResponse;
import com.litrpg.fitness.dto.VisibilityRequest;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * Friend requests and friendships between player accounts. Requires a player
 * JWT; every operation is scoped to the calling player — see
 * {@link com.litrpg.fitness.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** {@code GET /api/friends} — accepted friends. */
    @GetMapping
    public ResponseEntity<List<FriendshipResponse>> listFriends(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendService.listFriends(principal.getId()));
    }

    /**
     * {@code GET /api/friends/feed} — recent quest claims from accepted
     * friends, trimmed to what each friend has chosen to share.
     */
    @GetMapping("/feed")
    public ResponseEntity<List<FriendActivityEntry>> getActivityFeed(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendService.getActivityFeed(principal.getId()));
    }

    /** {@code GET /api/friends/requests} — pending requests received. */
    @GetMapping("/requests")
    public ResponseEntity<List<FriendshipResponse>> listIncomingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendService.listIncomingRequests(principal.getId()));
    }

    /** {@code GET /api/friends/requests/sent} — pending requests sent, awaiting response. */
    @GetMapping("/requests/sent")
    public ResponseEntity<List<FriendshipResponse>> listOutgoingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendService.listOutgoingRequests(principal.getId()));
    }

    /** {@code POST /api/friends/requests} — send a friend request by username. */
    @PostMapping("/requests")
    public ResponseEntity<FriendshipResponse> sendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FriendRequestDTO request) {
        FriendshipResponse response = friendService.sendRequest(principal.getId(), request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** {@code POST /api/friends/requests/{id}/accept} */
    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(friendService.respondToRequest(principal.getId(), id, true));
    }

    /** {@code POST /api/friends/requests/{id}/decline} */
    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<FriendshipResponse> declineRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(friendService.respondToRequest(principal.getId(), id, false));
    }

    /** {@code DELETE /api/friends/{id}} — remove a friend (unfriend). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        friendService.removeFriend(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /api/friends/{id}} — a friend's profile, trimmed to what they've chosen to share. */
    @GetMapping("/{id}")
    public ResponseEntity<FriendDetailResponse> getFriendDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(friendService.getFriendDetail(principal.getId(), id));
    }

    /** {@code GET /api/friends/settings} — the caller's default visibility level. */
    @GetMapping("/settings")
    public ResponseEntity<FriendSettingsResponse> getDefaultVisibility(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendService.getDefaultVisibility(principal.getId()));
    }

    /** {@code PUT /api/friends/settings} — set the default level of detail shown to friends. */
    @PutMapping("/settings")
    public ResponseEntity<FriendSettingsResponse> setDefaultVisibility(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VisibilityRequest request) {
        return ResponseEntity.ok(friendService.setDefaultVisibility(principal.getId(), request.getVisibility()));
    }

    /** {@code PUT /api/friends/{id}/visibility} — override the level of detail shown to one specific friend. */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<FriendshipResponse> setVisibilityForFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VisibilityRequest request) {
        return ResponseEntity.ok(friendService.setVisibilityForFriend(principal.getId(), id, request.getVisibility()));
    }
}
