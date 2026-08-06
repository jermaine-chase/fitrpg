package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.CharacterAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterAchievementRepository extends JpaRepository<CharacterAchievement, UUID> {

    List<CharacterAchievement> findByCharacterId(UUID characterId);

    boolean existsByCharacterIdAndAchievementCode(UUID characterId, String achievementCode);
}
