package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.ActivitySyncRequest;
import com.litrpg.fitness.dto.ActivitySyncResponse;
import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.ActivitySyncRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitySyncServiceTest {

    @Mock
    private ActivitySyncRecordRepository activitySyncRecordRepository;
    @Mock
    private CharacterService characterService;
    @Mock
    private GameEngineService gameEngineService;

    private ActivitySyncService activitySyncService;

    @BeforeEach
    void setUp() {
        activitySyncService = new ActivitySyncService(activitySyncRecordRepository, characterService, gameEngineService,
                1000, 500, 30, 10, 5);
    }

    @Test
    void syncActivity_rejectsDuplicateSourceAndDate() {
        UUID characterId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(characterService.getOwnedCharacter(characterId, userId)).thenReturn(new Character("Hero"));
        ActivitySyncRequest request = new ActivitySyncRequest();
        request.setSource("fitbit");
        request.setSteps(2000);
        request.setDate(LocalDate.of(2026, 1, 1));
        when(activitySyncRecordRepository.existsByCharacterIdAndSourceAndActivityDate(
                characterId, "fitbit", LocalDate.of(2026, 1, 1))).thenReturn(true);

        assertThatThrownBy(() -> activitySyncService.syncActivity(characterId, userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already synced");
    }

    @Test
    void syncActivity_convertsStepsAndMinutesAboveThresholdToXp() {
        UUID characterId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(characterService.getOwnedCharacter(characterId, userId)).thenReturn(new Character("Hero"));
        ActivitySyncRequest request = new ActivitySyncRequest();
        request.setSource("manual");
        request.setSteps(2000); // 1000 above threshold / 500 per unit = 2 units * 5 xp = 10 CON xp
        request.setActiveMinutes(50); // 20 above threshold / 10 per unit = 2 units * 5 xp = 10 STR xp

        ActivitySyncResponse fakeResponse = new ActivitySyncResponse(
                CharacterSheetResponse.from(new Character("Hero")), List.of(), List.of());
        when(gameEngineService.applyActivitySyncRewards(eq(characterId), any(), anyString())).thenReturn(fakeResponse);

        ActivitySyncResponse response = activitySyncService.syncActivity(characterId, userId, request);

        assertThat(response).isSameAs(fakeResponse);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<StatType, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(gameEngineService).applyActivitySyncRewards(eq(characterId), captor.capture(), eq("manual"));
        assertThat(captor.getValue()).containsEntry(StatType.CON, 10).containsEntry(StatType.STR, 10);
        verify(activitySyncRecordRepository).save(any());
    }

    @Test
    void syncActivity_belowThresholdYieldsNoXpEntries() {
        UUID characterId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(characterService.getOwnedCharacter(characterId, userId)).thenReturn(new Character("Hero"));
        ActivitySyncRequest request = new ActivitySyncRequest();
        request.setSource("manual");
        request.setSteps(100);
        request.setActiveMinutes(5);

        ActivitySyncResponse fakeResponse = new ActivitySyncResponse(
                CharacterSheetResponse.from(new Character("Hero")), List.of(), List.of());
        when(gameEngineService.applyActivitySyncRewards(eq(characterId), any(), anyString())).thenReturn(fakeResponse);

        activitySyncService.syncActivity(characterId, userId, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<StatType, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(gameEngineService).applyActivitySyncRewards(eq(characterId), captor.capture(), anyString());
        assertThat(captor.getValue()).isEmpty();
    }
}
