package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A friendship link between two users. Maps to the {@code friendships} table.
 * One row is created per pair, initiated by {@code requesterId}; its
 * {@code status} tracks whether {@code addresseeId} has accepted, declined,
 * or not yet responded.
 */
@Entity
@Table(name = "friendships")
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** Overrides the requester's {@code defaultFriendVisibility} for this specific friendship, if set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requester_visibility")
    private FriendVisibility requesterVisibility;

    /** Overrides the addressee's {@code defaultFriendVisibility} for this specific friendship, if set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "addressee_visibility")
    private FriendVisibility addresseeVisibility;

    public Friendship() {
    }

    public Friendship(UUID requesterId, UUID addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** The other party in this friendship, relative to {@code userId}. */
    public UUID otherUser(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    /** The per-friendship visibility override {@code userId} has set for the other party, if any. */
    public FriendVisibility visibilityOverrideBy(UUID userId) {
        return requesterId.equals(userId) ? requesterVisibility : addresseeVisibility;
    }

    /** Sets the per-friendship visibility override on behalf of {@code userId}. */
    public void setVisibilityOverrideBy(UUID userId, FriendVisibility visibility) {
        if (requesterId.equals(userId)) {
            requesterVisibility = visibility;
        } else {
            addresseeVisibility = visibility;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(UUID requesterId) {
        this.requesterId = requesterId;
    }

    public UUID getAddresseeId() {
        return addresseeId;
    }

    public void setAddresseeId(UUID addresseeId) {
        this.addresseeId = addresseeId;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public void setStatus(FriendshipStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public FriendVisibility getRequesterVisibility() {
        return requesterVisibility;
    }

    public void setRequesterVisibility(FriendVisibility requesterVisibility) {
        this.requesterVisibility = requesterVisibility;
    }

    public FriendVisibility getAddresseeVisibility() {
        return addresseeVisibility;
    }

    public void setAddresseeVisibility(FriendVisibility addresseeVisibility) {
        this.addresseeVisibility = addresseeVisibility;
    }
}
