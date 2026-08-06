package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.AdminCharacterUpdateRequest;
import com.litrpg.fitness.dto.QuestFormRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.service.CharacterService;
import com.litrpg.fitness.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private CharacterService characterService;
    @Mock
    private QuestRepository questRepository;
    @Mock
    private QuestService questService;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(characterService, questRepository, questService);
    }

    private Quest quest(String id, int minLevel) {
        Quest quest = new Quest();
        quest.setQuestId(id);
        quest.setTitle(id);
        quest.setTargetStat(StatType.STR);
        quest.setMinLevel(minLevel);
        quest.setStatus(QuestStatus.APPROVED);
        return quest;
    }

    @Test
    void listCharacters_mapsToSheets() {
        when(characterService.listCharacters()).thenReturn(List.of(new Character("Hero")));

        var response = controller.listCharacters();

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void updateCharacter_delegatesToService() {
        UUID id = UUID.randomUUID();
        AdminCharacterUpdateRequest request = new AdminCharacterUpdateRequest();
        request.setCharacterName("Updated");
        Character updated = new Character("Updated");
        when(characterService.updateCharacter(id, request)).thenReturn(updated);

        var response = controller.updateCharacter(id, request);

        assertThat(response.getBody().getCharacterName()).isEqualTo("Updated");
    }

    @Test
    void deleteCharacter_returnsNoContent() {
        UUID id = UUID.randomUUID();

        var response = controller.deleteCharacter(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(characterService).deleteCharacter(id);
    }

    @Test
    void listQuests_sortsByLevelThenId() {
        when(questRepository.findAll()).thenReturn(List.of(quest("Q-2", 5), quest("Q-1", 1)));

        var response = controller.listQuests();

        assertThat(response.getBody()).extracting(q -> q.getQuestId()).containsExactly("Q-1", "Q-2");
    }

    @Test
    void createQuest_rejectsBlankQuestId() {
        QuestFormRequest request = new QuestFormRequest();
        request.setQuestId("  ");

        assertThatThrownBy(() -> controller.createQuest(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("questId is required");
    }

    @Test
    void createQuest_rejectsDuplicateId() {
        QuestFormRequest request = new QuestFormRequest();
        request.setQuestId("Q-1");
        when(questRepository.existsById("Q-1")).thenReturn(true);

        assertThatThrownBy(() -> controller.createQuest(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createQuest_savesNewQuest() {
        QuestFormRequest request = new QuestFormRequest();
        request.setQuestId("Q-1");
        request.setTitle("Push-ups");
        request.setTargetStat(StatType.STR);
        when(questRepository.existsById("Q-1")).thenReturn(false);
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.createQuest(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getQuestId()).isEqualTo("Q-1");
        assertThat(response.getBody().getTitle()).isEqualTo("Push-ups");
    }

    @Test
    void updateQuest_throwsWhenMissing() {
        when(questRepository.findById("Q-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateQuest("Q-1", new QuestFormRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateQuest_overwritesFields() {
        Quest existing = quest("Q-1", 1);
        when(questRepository.findById("Q-1")).thenReturn(Optional.of(existing));
        when(questRepository.save(existing)).thenReturn(existing);
        QuestFormRequest request = new QuestFormRequest();
        request.setTitle("New Title");
        request.setTargetStat(StatType.DEX);

        var response = controller.updateQuest("Q-1", request);

        assertThat(response.getBody().getTitle()).isEqualTo("New Title");
        assertThat(response.getBody().getTargetStat()).isEqualTo(StatType.DEX);
    }

    @Test
    void deleteQuest_throwsWhenMissing() {
        when(questRepository.existsById("Q-1")).thenReturn(false);

        assertThatThrownBy(() -> controller.deleteQuest("Q-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteQuest_deletesWhenPresent() {
        when(questRepository.existsById("Q-1")).thenReturn(true);

        var response = controller.deleteQuest("Q-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(questRepository).deleteById("Q-1");
    }

    @Test
    void listPendingQuests_delegatesToService() {
        when(questService.listPending()).thenReturn(List.of(quest("Q-1", 1)));

        var response = controller.listPendingQuests();

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void approveQuest_delegatesToService() {
        Quest approved = quest("Q-1", 1);
        when(questService.approve("Q-1")).thenReturn(approved);

        var response = controller.approveQuest("Q-1");

        assertThat(response.getBody().getQuestId()).isEqualTo("Q-1");
    }

    @Test
    void rejectQuest_delegatesToService() {
        Quest rejected = quest("Q-1", 1);
        when(questService.reject("Q-1")).thenReturn(rejected);

        var response = controller.rejectQuest("Q-1");

        assertThat(response.getBody().getQuestId()).isEqualTo("Q-1");
    }
}
