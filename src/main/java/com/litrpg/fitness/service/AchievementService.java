package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AchievementResponse;
import com.litrpg.fitness.model.Achievement;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterAchievement;
import com.litrpg.fitness.repository.AchievementRepository;
import com.litrpg.fitness.repository.CharacterAchievementRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evaluates and unlocks badges. Criteria are checked against data already
 * captured on {@link Character} and in {@code workout_logs} — there is no
 * separate polling job; evaluation happens inline at claim time (see
 * {@link GameEngineService#claimQuestRewards}).
 */
@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final CharacterAchievementRepository characterAchievementRepository;
    private final WorkoutLogRepository workoutLogRepository;

    public AchievementService(AchievementRepository achievementRepository,
                               CharacterAchievementRepository characterAchievementRepository,
                               WorkoutLogRepository workoutLogRepository) {
        this.achievementRepository = achievementRepository;
        this.characterAchievementRepository = characterAchievementRepository;
        this.workoutLogRepository = workoutLogRepository;
    }

    /**
     * Checks every not-yet-unlocked achievement against the character's
     * current state (post-claim) and persists any newly earned ones.
     *
     * @return the achievements newly unlocked by this evaluation, if any
     */
    @Transactional
    public List<Achievement> evaluateUnlocks(Character character) {
        long totalClaims = workoutLogRepository.countByCharacterId(character.getId());

        Set<String> alreadyUnlocked = characterAchievementRepository.findByCharacterId(character.getId()).stream()
                .map(CharacterAchievement::getAchievementCode)
                .collect(Collectors.toSet());

        List<Achievement> newlyUnlocked = new ArrayList<>();
        for (Achievement a : achievementRepository.findAll()) {
            if (alreadyUnlocked.contains(a.getCode())) {
                continue;
            }
            if (isEarned(a, character, totalClaims)) {
                characterAchievementRepository.save(new CharacterAchievement(character.getId(), a.getCode()));
                newlyUnlocked.add(a);
            }
        }
        return newlyUnlocked;
    }

    private boolean isEarned(Achievement a, Character character, long totalClaims) {
        return switch (a.getCriteriaType()) {
            case FIRST_CLAIM, TOTAL_CLAIMS_MILESTONE -> totalClaims >= a.getThreshold();
            case LEVEL_MILESTONE -> character.getCurrentLevel() >= a.getThreshold();
            case STREAK_MILESTONE -> character.getStreakCount() >= a.getThreshold();
            case ALL_STATS_LEVEL -> character.getStats().stream()
                    .allMatch(s -> s.getCurrentLevel() >= a.getThreshold());
        };
    }

    /** The full badge catalog for a character, locked entries included. */
    @Transactional(readOnly = true)
    public List<AchievementResponse> getCatalogForCharacter(UUID characterId) {
        Map<String, CharacterAchievement> unlockedByCode = characterAchievementRepository.findByCharacterId(characterId)
                .stream()
                .collect(Collectors.toMap(CharacterAchievement::getAchievementCode, ca -> ca));

        return achievementRepository.findAll().stream()
                .sorted(Comparator.comparing(Achievement::getCode))
                .map(a -> {
                    CharacterAchievement unlock = unlockedByCode.get(a.getCode());
                    return unlock != null
                            ? AchievementResponse.unlocked(a, unlock.getUnlockedAt())
                            : AchievementResponse.locked(a);
                })
                .collect(Collectors.toList());
    }
}
