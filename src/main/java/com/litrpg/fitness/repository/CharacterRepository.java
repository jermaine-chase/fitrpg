package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    /**
     * Characters who have a recorded last workout date strictly before
     * {@code today} - i.e. they did not train today and are therefore subject
     * to the Midnight Decay Engine. Characters that have never worked out
     * (null date) are intentionally excluded: there is nothing to decay yet.
     */
    @Query("SELECT c FROM Character c "
            + "WHERE c.lastWorkoutDate IS NOT NULL AND c.lastWorkoutDate < :today")
    List<Character> findInactiveCharacters(@Param("today") LocalDate today);
}
