package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.DailyQuestAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyQuestAssignmentRepository extends JpaRepository<DailyQuestAssignment, UUID> {

    Optional<DailyQuestAssignment> findByCharacterIdAndAssignedDate(UUID characterId, LocalDate assignedDate);
}
