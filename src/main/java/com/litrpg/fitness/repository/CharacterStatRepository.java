package com.litrpg.fitness.repository;

import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.StatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CharacterStatRepository extends JpaRepository<CharacterStat, UUID> {

    Optional<CharacterStat> findByCharacterIdAndStatType(UUID characterId, StatType statType);
}
