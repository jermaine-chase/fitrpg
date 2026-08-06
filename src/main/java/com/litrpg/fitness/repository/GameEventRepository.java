package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.GameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GameEventRepository extends JpaRepository<GameEvent, UUID> {

    /** Events currently running at the given instant (inclusive of both endpoints). */
    @Query("SELECT e FROM GameEvent e WHERE e.startAt <= :now AND e.endAt >= :now")
    List<GameEvent> findActive(@Param("now") LocalDateTime now);
}
