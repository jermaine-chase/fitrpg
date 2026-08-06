package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.QuestTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestRepository extends JpaRepository<Quest, String> {

    /** Returns all quests whose min_level is <= the given character level, regardless of status. */
    List<Quest> findByMinLevelLessThanEqual(int characterLevel);

    /** Returns all quests carrying the given category chip, regardless of status. */
    List<Quest> findByTag(QuestTag tag);

    /** Returns quests unlocked at a level, further filtered to one category chip, regardless of status. */
    List<Quest> findByMinLevelLessThanEqualAndTag(int characterLevel, QuestTag tag);

    /** Quests in a given moderation status — used for the public catalog (APPROVED) and the admin review queue (PENDING). */
    List<Quest> findByStatus(QuestStatus status);

    List<Quest> findByMinLevelLessThanEqualAndStatus(int characterLevel, QuestStatus status);

    List<Quest> findByTagAndStatus(QuestTag tag, QuestStatus status);

    List<Quest> findByMinLevelLessThanEqualAndTagAndStatus(int characterLevel, QuestTag tag, QuestStatus status);
}
