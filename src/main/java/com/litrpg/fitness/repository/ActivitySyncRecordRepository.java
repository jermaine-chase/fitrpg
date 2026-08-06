package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.ActivitySyncRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ActivitySyncRecordRepository extends JpaRepository<ActivitySyncRecord, UUID> {

    /** Guards against double-crediting the same character/source/day combination. */
    boolean existsByCharacterIdAndSourceAndActivityDate(UUID characterId, String source, LocalDate activityDate);
}
