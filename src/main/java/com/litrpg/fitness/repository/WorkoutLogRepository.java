package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.WorkoutLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {

    List<WorkoutLog> findByCharacterIdOrderByLoggedAtDesc(UUID characterId);
}
