package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.QuestSubmissionRequest;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.QuestTag;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestControllerTest {

    @Mock
    private QuestRepository questRepository;
    @Mock
    private QuestService questService;

    private QuestController controller;

    @BeforeEach
    void setUp() {
        controller = new QuestController(questRepository, questService);
    }

    private Quest quest(String id) {
        Quest quest = new Quest();
        quest.setQuestId(id);
        quest.setTitle(id);
        quest.setTargetStat(StatType.STR);
        quest.setStatus(QuestStatus.APPROVED);
        return quest;
    }

    @Test
    void listQuests_noFiltersUsesStatusOnly() {
        when(questRepository.findByStatus(QuestStatus.APPROVED)).thenReturn(List.of(quest("Q-1")));

        var response = controller.listQuests(null, null);

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listQuests_levelFilterOnly() {
        when(questRepository.findByMinLevelLessThanEqualAndStatus(5, QuestStatus.APPROVED))
                .thenReturn(List.of(quest("Q-1")));

        var response = controller.listQuests(5, null);

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listQuests_tagFilterOnly() {
        when(questRepository.findByTagAndStatus(QuestTag.CARDIO, QuestStatus.APPROVED))
                .thenReturn(List.of(quest("Q-1")));

        var response = controller.listQuests(null, QuestTag.CARDIO);

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listQuests_levelAndTagFilters() {
        when(questRepository.findByMinLevelLessThanEqualAndTagAndStatus(5, QuestTag.CARDIO, QuestStatus.APPROVED))
                .thenReturn(List.of(quest("Q-1")));

        var response = controller.listQuests(5, QuestTag.CARDIO);

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void submitQuest_returns201WithSubmittedQuest() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "hero");
        QuestSubmissionRequest request = new QuestSubmissionRequest();
        request.setTitle("Push-ups");
        request.setTargetStat(StatType.STR);
        Quest submitted = quest("PQ-1");
        submitted.setStatus(QuestStatus.PENDING);
        when(questService.submitQuest(userId, request)).thenReturn(submitted);

        var response = controller.submitQuest(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getQuestId()).isEqualTo("PQ-1");
    }
}
