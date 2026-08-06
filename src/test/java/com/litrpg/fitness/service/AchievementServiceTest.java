package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AchievementResponse;
import com.litrpg.fitness.model.Achievement;
import com.litrpg.fitness.model.AchievementCriteriaType;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterAchievement;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.AchievementRepository;
import com.litrpg.fitness.repository.CharacterAchievementRepository;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private CharacterAchievementRepository characterAchievementRepository;
    @Mock
    private WorkoutLogRepository workoutLogRepository;
    @Mock
    private CharacterRepository characterRepository;

    private AchievementService achievementService;

    @BeforeEach
    void setUp() {
        achievementService = new AchievementService(achievementRepository, characterAchievementRepository,
                workoutLogRepository, characterRepository);
    }

    private Achievement achievement(String code, AchievementCriteriaType type, int threshold) {
        Achievement a = new Achievement();
        a.setCode(code);
        a.setName(code);
        a.setDescription(code);
        a.setIcon("icon");
        a.setCriteriaType(type);
        a.setThreshold(threshold);
        return a;
    }

    private Character characterWithLevel(int level, int streak) {
        Character character = new Character("Hero");
        character.setId(UUID.randomUUID());
        character.setCurrentLevel(level);
        character.setStreakCount(streak);
        for (StatType type : StatType.values()) {
            character.addStat(new CharacterStat(type));
        }
        return character;
    }

    @Test
    void evaluateUnlocks_unlocksFirstClaimAchievement() {
        Character character = characterWithLevel(1, 0);
        when(workoutLogRepository.countByCharacterId(character.getId())).thenReturn(1L);
        when(characterAchievementRepository.findByCharacterId(character.getId())).thenReturn(List.of());
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("FIRST_CLAIM", AchievementCriteriaType.FIRST_CLAIM, 1)));

        List<Achievement> unlocked = achievementService.evaluateUnlocks(character);

        assertThat(unlocked).extracting(Achievement::getCode).containsExactly("FIRST_CLAIM");
        verify(characterAchievementRepository).save(any(CharacterAchievement.class));
    }

    @Test
    void evaluateUnlocks_skipsAlreadyUnlockedAchievements() {
        Character character = characterWithLevel(1, 0);
        when(workoutLogRepository.countByCharacterId(character.getId())).thenReturn(5L);
        CharacterAchievement existing = new CharacterAchievement(character.getId(), "FIRST_CLAIM");
        when(characterAchievementRepository.findByCharacterId(character.getId())).thenReturn(List.of(existing));
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("FIRST_CLAIM", AchievementCriteriaType.FIRST_CLAIM, 1)));

        List<Achievement> unlocked = achievementService.evaluateUnlocks(character);

        assertThat(unlocked).isEmpty();
        verify(characterAchievementRepository, never()).save(any());
    }

    @Test
    void evaluateUnlocks_levelMilestoneNotYetReached() {
        Character character = characterWithLevel(3, 0);
        when(workoutLogRepository.countByCharacterId(character.getId())).thenReturn(0L);
        when(characterAchievementRepository.findByCharacterId(character.getId())).thenReturn(List.of());
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("LEVEL_10", AchievementCriteriaType.LEVEL_MILESTONE, 10)));

        List<Achievement> unlocked = achievementService.evaluateUnlocks(character);

        assertThat(unlocked).isEmpty();
    }

    @Test
    void evaluateUnlocks_streakMilestoneGrantsFreezeAndSavesCharacter() {
        Character character = characterWithLevel(1, 7);
        when(workoutLogRepository.countByCharacterId(character.getId())).thenReturn(0L);
        when(characterAchievementRepository.findByCharacterId(character.getId())).thenReturn(List.of());
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("STREAK_7", AchievementCriteriaType.STREAK_MILESTONE, 7)));
        int freezesBefore = character.getStreakFreezeCount();

        List<Achievement> unlocked = achievementService.evaluateUnlocks(character);

        assertThat(unlocked).extracting(Achievement::getCode).containsExactly("STREAK_7");
        assertThat(character.getStreakFreezeCount()).isEqualTo(freezesBefore + 1);
        verify(characterRepository).save(character);
    }

    @Test
    void evaluateUnlocks_allStatsLevelRequiresEveryStat() {
        Character character = characterWithLevel(1, 0);
        character.getStats().forEach(s -> s.setCurrentLevel(5));
        when(workoutLogRepository.countByCharacterId(character.getId())).thenReturn(0L);
        when(characterAchievementRepository.findByCharacterId(character.getId())).thenReturn(List.of());
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("ALL_5", AchievementCriteriaType.ALL_STATS_LEVEL, 5)));

        List<Achievement> unlocked = achievementService.evaluateUnlocks(character);

        assertThat(unlocked).extracting(Achievement::getCode).containsExactly("ALL_5");
    }

    @Test
    void getCatalogForCharacter_marksUnlockedAndLockedEntries() {
        UUID characterId = UUID.randomUUID();
        CharacterAchievement unlock = new CharacterAchievement(characterId, "FIRST_CLAIM");
        when(characterAchievementRepository.findByCharacterId(characterId)).thenReturn(List.of(unlock));
        when(achievementRepository.findAll()).thenReturn(List.of(
                achievement("FIRST_CLAIM", AchievementCriteriaType.FIRST_CLAIM, 1),
                achievement("LEVEL_10", AchievementCriteriaType.LEVEL_MILESTONE, 10)));

        List<AchievementResponse> catalog = achievementService.getCatalogForCharacter(characterId);

        assertThat(catalog).hasSize(2);
        assertThat(catalog.get(0).code()).isEqualTo("FIRST_CLAIM");
        assertThat(catalog.get(0).unlocked()).isTrue();
        assertThat(catalog.get(1).code()).isEqualTo("LEVEL_10");
        assertThat(catalog.get(1).unlocked()).isFalse();
    }
}
