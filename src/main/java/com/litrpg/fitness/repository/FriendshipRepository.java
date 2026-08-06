package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.Friendship;
import com.litrpg.fitness.model.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /** Any existing friendship between the two users, regardless of who requested or the direction. */
    @Query("SELECT f FROM Friendship f WHERE "
            + "(f.requesterId = :userA AND f.addresseeId = :userB) "
            + "OR (f.requesterId = :userB AND f.addresseeId = :userA)")
    Optional<Friendship> findBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** All accepted friendships where the user is either party. */
    @Query("SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' "
            + "AND (f.requesterId = :userId OR f.addresseeId = :userId)")
    List<Friendship> findAcceptedForUser(@Param("userId") UUID userId);

    /** Pending requests sent to the user, awaiting their response. */
    List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);

    /** Pending requests the user has sent, awaiting the other party's response. */
    List<Friendship> findByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);
}
