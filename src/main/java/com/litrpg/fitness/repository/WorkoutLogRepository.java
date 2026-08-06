package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.WorkoutLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {

    List<WorkoutLog> findByCharacterIdOrderByLoggedAtDesc(UUID characterId);

    boolean existsByCharacterIdAndQuestIdAndLoggedAtBetween(
            UUID characterId, String questId, LocalDateTime start, LocalDateTime end);

    /** Recent claims across a set of characters — powers the friend activity feed. */
    List<WorkoutLog> findTop50ByCharacterIdInAndLoggedAtAfterOrderByLoggedAtDesc(
            List<UUID> characterIds, LocalDateTime since);
}
