package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.Quest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestRepository extends JpaRepository<Quest, String> {

    /** Returns all quests whose min_level is <= the given character level. */
    List<Quest> findByMinLevelLessThanEqual(int characterLevel);
}
