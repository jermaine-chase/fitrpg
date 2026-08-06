package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.DailyQuestResponse;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.DailyQuestAssignment;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.DailyQuestAssignmentRepository;
import com.litrpg.fitness.repository.QuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuestServiceTest {

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private QuestRepository questRepository;
    @Mock
    private DailyQuestAssignmentRepository dailyQuestAssignmentRepository;

    private DailyQuestService dailyQuestService;

    @BeforeEach
    void setUp() {
        dailyQuestService = new DailyQuestService(characterRepository, questRepository, dailyQuestAssignmentRepository);
    }

    private Character characterWithStats() {
        Character character = new Character("Hero");
        character.setId(UUID.randomUUID());
        for (StatType type : StatType.values()) {
            character.addStat(new CharacterStat(type));
        }
        return character;
    }

    private Quest quest(String id, StatType stat, int minLevel) {
        Quest quest = new Quest();
        quest.setQuestId(id);
        quest.setTitle(id);
        quest.setTargetStat(stat);
        quest.setMinLevel(minLevel);
        quest.setStatus(QuestStatus.APPROVED);
        return quest;
    }

    @Test
    void getOrAssignDailyQuest_returnsExistingAssignmentForToday() {
        UUID characterId = UUID.randomUUID();
        Quest quest = quest("Q-1", StatType.STR, 1);
        DailyQuestAssignment assignment = new DailyQuestAssignment(characterId, "Q-1", LocalDate.now());
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now()))
                .thenReturn(Optional.of(assignment));
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(quest));

        DailyQuestResponse response = dailyQuestService.getOrAssignDailyQuest(characterId);

        assertThat(response.getQuest().getQuestId()).isEqualTo("Q-1");
        assertThat(response.getBonusMultiplier()).isEqualTo(DailyQuestService.DAILY_FOCUS_BONUS_MULTIPLIER);
    }

    @Test
    void getOrAssignDailyQuest_throwsWhenAssignedQuestNoLongerExists() {
        UUID characterId = UUID.randomUUID();
        DailyQuestAssignment assignment = new DailyQuestAssignment(characterId, "Q-GONE", LocalDate.now());
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now()))
                .thenReturn(Optional.of(assignment));
        when(questRepository.findById("Q-GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyQuestService.getOrAssignDailyQuest(characterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrAssignDailyQuest_throwsWhenCharacterMissing() {
        UUID characterId = UUID.randomUUID();
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyQuestService.getOrAssignDailyQuest(characterId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrAssignDailyQuest_throwsWhenNoQuestsUnlocked() {
        Character character = characterWithStats();
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(character.getId(), LocalDate.now()))
                .thenReturn(Optional.empty());
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        when(questRepository.findByMinLevelLessThanEqualAndStatus(character.getCurrentLevel(), QuestStatus.APPROVED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> dailyQuestService.getOrAssignDailyQuest(character.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrAssignDailyQuest_prefersWeakestStatQuestAndPersistsAssignment() {
        Character character = characterWithStats();
        character.getStats().stream()
                .filter(s -> s.getStatType() != StatType.DEX)
                .forEach(s -> s.setCurrentLevel(5));
        // DEX is the only stat left at level 1, so it is unambiguously the weakest.
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(character.getId(), LocalDate.now()))
                .thenReturn(Optional.empty());
        when(characterRepository.findById(character.getId())).thenReturn(Optional.of(character));
        Quest dexQuest = quest("Q-DEX", StatType.DEX, 1);
        Quest strQuest = quest("Q-STR", StatType.STR, 1);
        when(questRepository.findByMinLevelLessThanEqualAndStatus(character.getCurrentLevel(), QuestStatus.APPROVED))
                .thenReturn(List.of(dexQuest, strQuest));

        DailyQuestResponse response = dailyQuestService.getOrAssignDailyQuest(character.getId());

        assertThat(response.getQuest().getQuestId()).isEqualTo("Q-DEX");
    }

    @Test
    void isTodaysDailyQuest_trueWhenMatches() {
        UUID characterId = UUID.randomUUID();
        DailyQuestAssignment assignment = new DailyQuestAssignment(characterId, "Q-1", LocalDate.now());
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now()))
                .thenReturn(Optional.of(assignment));

        assertThat(dailyQuestService.isTodaysDailyQuest(characterId, "Q-1")).isTrue();
        assertThat(dailyQuestService.isTodaysDailyQuest(characterId, "Q-2")).isFalse();
    }

    @Test
    void isTodaysDailyQuest_falseWhenNoneAssigned() {
        UUID characterId = UUID.randomUUID();
        when(dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now()))
                .thenReturn(Optional.empty());

        assertThat(dailyQuestService.isTodaysDailyQuest(characterId, "Q-1")).isFalse();
    }
}
