package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.ActivitySyncResponse;
import com.litrpg.fitness.dto.ClaimRewardResponse;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Achievement;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEngineServiceTest {

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private QuestRepository questRepository;
    @Mock
    private WorkoutLogRepository workoutLogRepository;
    @Mock
    private DailyQuestService dailyQuestService;
    @Mock
    private AchievementService achievementService;
    @Mock
    private GameEventService gameEventService;

    private GameEngineService gameEngineService;

    @BeforeEach
    void setUp() {
        gameEngineService = new GameEngineService(characterRepository, questRepository, workoutLogRepository,
                dailyQuestService, achievementService, gameEventService);
        lenient().when(gameEventService.getActiveMultiplier(any())).thenReturn(1.0);
        lenient().when(dailyQuestService.isTodaysDailyQuest(any(), anyString())).thenReturn(false);
        lenient().when(achievementService.evaluateUnlocks(any())).thenReturn(List.of());
        lenient().when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Character freshCharacter() {
        Character character = new Character("Hero");
        character.setId(UUID.randomUUID());
        for (StatType type : StatType.values()) {
            character.addStat(new CharacterStat(type));
        }
        return character;
    }

    private Quest approvedQuest(StatType stat, int baseCharacterXp, int baseStatXp) {
        Quest quest = new Quest();
        quest.setQuestId("Q-1");
        quest.setTitle("Push-ups");
        quest.setTargetStat(stat);
        quest.setBaseCharacterXp(baseCharacterXp);
        quest.setBaseStatXp(baseStatXp);
        quest.setStatus(QuestStatus.APPROVED);
        return quest;
    }

    @Test
    void claimQuestRewards_throwsWhenCharacterMissing() {
        UUID characterId = UUID.randomUUID();
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameEngineService.claimQuestRewards(characterId, "Q-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void claimQuestRewards_throwsWhenQuestNotApproved() {
        Character character = freshCharacter();
        Quest pending = approvedQuest(StatType.STR, 100, 50);
        pending.setStatus(QuestStatus.PENDING);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> gameEngineService.claimQuestRewards(character.getId(), "Q-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void claimQuestRewards_throwsWhenAlreadyClaimedToday() {
        Character character = freshCharacter();
        Quest quest = approvedQuest(StatType.STR, 100, 50);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));
        when(workoutLogRepository.existsByCharacterIdAndQuestIdAndLoggedAtBetween(any(), anyString(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> gameEngineService.claimQuestRewards(character.getId(), "Q-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been claimed today");
    }

    @Test
    void claimQuestRewards_appliesXpAndLevelsUp() {
        Character character = freshCharacter();
        Quest quest = approvedQuest(StatType.STR, 100, 50);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));
        when(workoutLogRepository.existsByCharacterIdAndQuestIdAndLoggedAtBetween(any(), anyString(), any(), any()))
                .thenReturn(false);

        ClaimRewardResponse response = gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(response.character().getCurrentLevel()).isEqualTo(2);
        assertThat(response.character().getOverallXp()).isIn(0, 50);
        assertThat(response.newAchievements()).isEmpty();
        assertThat(response.dailyFocusBonusXp()).isZero();
        verify(characterRepository).save(character);
    }

    @Test
    void claimQuestRewards_appliesDailyFocusBonus() {
        Character character = freshCharacter();
        Quest quest = approvedQuest(StatType.DEX, 100, 50);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));
        when(workoutLogRepository.existsByCharacterIdAndQuestIdAndLoggedAtBetween(any(), anyString(), any(), any()))
                .thenReturn(false);
        when(dailyQuestService.isTodaysDailyQuest(character.getId(), "Q-1")).thenReturn(true);

        ClaimRewardResponse response = gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(response.dailyFocusBonusXp()).isGreaterThan(0);
    }

    @Test
    void computeNewStreak_startsAtOneForFirstWorkout() {
        Character character = freshCharacter();
        Quest quest = approvedQuest(StatType.CON, 10, 5);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(character.getStreakCount()).isEqualTo(1);
    }

    @Test
    void computeNewStreak_incrementsOnConsecutiveDay() {
        Character character = freshCharacter();
        character.setLastWorkoutDate(LocalDate.now().minusDays(1));
        character.setStreakCount(5);
        Quest quest = approvedQuest(StatType.CON, 10, 5);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(character.getStreakCount()).isEqualTo(6);
    }

    @Test
    void computeNewStreak_unchangedIfAlreadyTrainedToday() {
        Character character = freshCharacter();
        character.setLastWorkoutDate(LocalDate.now());
        character.setStreakCount(5);
        Quest quest = approvedQuest(StatType.CON, 10, 5);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(character.getStreakCount()).isEqualTo(5);
    }

    @Test
    void computeNewStreak_usesFreezeWhenAvailableAfterMissedDays() {
        Character character = freshCharacter();
        character.setLastWorkoutDate(LocalDate.now().minusDays(3));
        character.setStreakCount(5);
        character.setStreakFreezeCount(1);
        Quest quest = approvedQuest(StatType.CON, 10, 5);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(character.getStreakCount()).isEqualTo(5);
        assertThat(character.getStreakFreezeCount()).isZero();
    }

    @Test
    void computeNewStreak_halvesWhenNoFreezeAfterMissedDays() {
        Character character = freshCharacter();
        character.setLastWorkoutDate(LocalDate.now().minusDays(3));
        character.setStreakCount(5);
        character.setStreakFreezeCount(0);
        Quest quest = approvedQuest(StatType.CON, 10, 5);
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        gameEngineService.claimQuestRewards(character.getId(), "Q-1");

        assertThat(character.getStreakCount()).isEqualTo(2);
    }

    @Test
    void applyActivitySyncRewards_skipsNonPositiveAmounts() {
        Character character = freshCharacter();
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        Map<StatType, Integer> amounts = new EnumMap<>(StatType.class);
        amounts.put(StatType.STR, 0);
        amounts.put(StatType.CON, null);

        ActivitySyncResponse response = gameEngineService.applyActivitySyncRewards(character.getId(), amounts, "manual");

        assertThat(response.rewards()).isEmpty();
        verify(characterRepository, never()).save(any());
    }

    @Test
    void applyActivitySyncRewards_appliesXpForPositiveStats() {
        Character character = freshCharacter();
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        Map<StatType, Integer> amounts = new EnumMap<>(StatType.class);
        amounts.put(StatType.CON, 40);

        ActivitySyncResponse response = gameEngineService.applyActivitySyncRewards(character.getId(), amounts, "manual");

        assertThat(response.rewards()).hasSize(1);
        assertThat(response.rewards().get(0).stat()).isEqualTo(StatType.CON);
        assertThat(response.rewards().get(0).xpAwarded()).isGreaterThan(0);
        verify(characterRepository).save(character);
    }

    @Test
    void applyActivitySyncRewards_throwsWhenCharacterMissing() {
        UUID characterId = UUID.randomUUID();
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameEngineService.applyActivitySyncRewards(characterId, Map.of(StatType.STR, 10), "manual"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void xpForNextLevel_delegatesToGameFormulas() {
        assertThat(gameEngineService.xpForNextLevel(1)).isEqualTo(GameFormulas.xpForNextLevel(1));
    }
}
