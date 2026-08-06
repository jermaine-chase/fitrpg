package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** Prunes rows for tokens that would now be rejected as expired anyway. */
    @Modifying
    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
