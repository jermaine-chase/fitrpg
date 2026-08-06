package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.QuestSubmissionRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.QuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock
    private QuestRepository questRepository;

    private QuestService questService;

    @BeforeEach
    void setUp() {
        questService = new QuestService(questRepository);
    }

    @Test
    void submitQuest_createsPendingQuestFromRequest() {
        UUID userId = UUID.randomUUID();
        QuestSubmissionRequest request = new QuestSubmissionRequest();
        request.setTitle("Run a 5k");
        request.setDescription("Run 5 kilometers");
        request.setTargetStat(StatType.CON);
        request.setBaseCharacterXp(75);
        request.setBaseStatXp(60);
        request.setMinLevel(3);
        request.setEstimatedMinutes(30);
        when(questRepository.save(any(Quest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Quest quest = questService.submitQuest(userId, request);

        assertThat(quest.getQuestId()).startsWith("PQ-");
        assertThat(quest.getTitle()).isEqualTo("Run a 5k");
        assertThat(quest.getDescription()).isEqualTo("Run 5 kilometers");
        assertThat(quest.getTargetStat()).isEqualTo(StatType.CON);
        assertThat(quest.getBaseCharacterXp()).isEqualTo(75);
        assertThat(quest.getBaseStatXp()).isEqualTo(60);
        assertThat(quest.getMinLevel()).isEqualTo(3);
        assertThat(quest.getEstimatedMinutes()).isEqualTo(30);
        assertThat(quest.getStatus()).isEqualTo(QuestStatus.PENDING);
        assertThat(quest.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    void listPending_returnsOnlyPendingQuests() {
        List<Quest> pending = List.of(new Quest());
        when(questRepository.findByStatus(QuestStatus.PENDING)).thenReturn(pending);

        assertThat(questService.listPending()).isEqualTo(pending);
    }

    @Test
    void approve_setsStatusToApproved() {
        Quest quest = new Quest();
        quest.setQuestId("PQ-ABCD1234");
        quest.setStatus(QuestStatus.PENDING);
        when(questRepository.findById("PQ-ABCD1234")).thenReturn(Optional.of(quest));
        when(questRepository.save(quest)).thenReturn(quest);

        Quest approved = questService.approve("PQ-ABCD1234");

        assertThat(approved.getStatus()).isEqualTo(QuestStatus.APPROVED);
    }

    @Test
    void reject_setsStatusToRejected() {
        Quest quest = new Quest();
        quest.setQuestId("PQ-ABCD1234");
        quest.setStatus(QuestStatus.PENDING);
        when(questRepository.findById("PQ-ABCD1234")).thenReturn(Optional.of(quest));
        when(questRepository.save(quest)).thenReturn(quest);

        Quest rejected = questService.reject("PQ-ABCD1234");

        assertThat(rejected.getStatus()).isEqualTo(QuestStatus.REJECTED);
    }

    @Test
    void approve_throwsWhenQuestMissing() {
        when(questRepository.findById("PQ-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questService.approve("PQ-MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuest_generatesUniqueQuestIds() {
        when(questRepository.save(any(Quest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuestSubmissionRequest request = new QuestSubmissionRequest();
        request.setTitle("Push-ups");
        request.setTargetStat(StatType.STR);

        ArgumentCaptor<Quest> captor = ArgumentCaptor.forClass(Quest.class);
        questService.submitQuest(UUID.randomUUID(), request);
        questService.submitQuest(UUID.randomUUID(), request);
        verify(questRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<Quest> saved = captor.getAllValues();
        assertThat(saved.get(0).getQuestId()).isNotEqualTo(saved.get(1).getQuestId());
    }
}
