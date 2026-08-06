package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.ActivitySyncRequest;
import com.litrpg.fitness.dto.ActivitySyncResponse;
import com.litrpg.fitness.dto.CharacterCustomizationRequest;
import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.dto.ClaimQuestRequest;
import com.litrpg.fitness.dto.ClaimRewardResponse;
import com.litrpg.fitness.dto.CreateCharacterRequest;
import com.litrpg.fitness.dto.DailyQuestResponse;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.AchievementService;
import com.litrpg.fitness.service.ActivitySyncService;
import com.litrpg.fitness.service.CharacterService;
import com.litrpg.fitness.service.DailyQuestService;
import com.litrpg.fitness.service.GameEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterControllerTest {

    @Mock
    private CharacterService characterService;
    @Mock
    private GameEngineService gameEngineService;
    @Mock
    private DailyQuestService dailyQuestService;
    @Mock
    private AchievementService achievementService;
    @Mock
    private ActivitySyncService activitySyncService;

    private CharacterController controller;
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(userId, "hero");

    @BeforeEach
    void setUp() {
        controller = new CharacterController(characterService, gameEngineService, dailyQuestService,
                achievementService, activitySyncService);
    }

    @Test
    void createCharacter_returns201() {
        CreateCharacterRequest request = new CreateCharacterRequest();
        request.setCharacterName("Hero");
        Character created = new Character("Hero");
        when(characterService.createCharacter("Hero", userId)).thenReturn(created);

        var response = controller.createCharacter(principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getCharacterName()).isEqualTo("Hero");
    }

    @Test
    void getMyCharacter_returnsSheet() {
        Character character = new Character("Hero");
        when(characterService.getMyCharacter(userId)).thenReturn(character);

        var response = controller.getMyCharacter(principal);

        assertThat(response.getBody().getCharacterName()).isEqualTo("Hero");
    }

    @Test
    void getCharacter_returnsOwnedCharacter() {
        UUID id = UUID.randomUUID();
        Character character = new Character("Hero");
        when(characterService.getOwnedCharacter(id, userId)).thenReturn(character);

        var response = controller.getCharacter(principal, id);

        assertThat(response.getBody().getCharacterName()).isEqualTo("Hero");
    }

    @Test
    void claimQuest_checksOwnershipThenClaims() {
        UUID id = UUID.randomUUID();
        ClaimQuestRequest request = new ClaimQuestRequest();
        request.setQuestId("Q-1");
        ClaimRewardResponse expected = new ClaimRewardResponse(
                CharacterSheetResponse.from(new Character("Hero")), null, 0, List.of());
        when(gameEngineService.claimQuestRewards(id, "Q-1")).thenReturn(expected);

        var response = controller.claimQuest(principal, id, request);

        verify(characterService).getOwnedCharacter(id, userId);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void getDailyQuest_checksOwnershipThenDelegates() {
        UUID id = UUID.randomUUID();
        DailyQuestResponse expected = new DailyQuestResponse(null, 1.25);
        when(dailyQuestService.getOrAssignDailyQuest(id)).thenReturn(expected);

        var response = controller.getDailyQuest(principal, id);

        verify(characterService).getOwnedCharacter(id, userId);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void getWorkoutHistory_delegatesToService() {
        UUID id = UUID.randomUUID();
        when(characterService.getOwnedWorkoutHistory(id, userId)).thenReturn(List.of());

        var response = controller.getWorkoutHistory(principal, id);

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getAchievements_checksOwnershipThenDelegates() {
        UUID id = UUID.randomUUID();
        when(achievementService.getCatalogForCharacter(id)).thenReturn(List.of());

        var response = controller.getAchievements(principal, id);

        verify(characterService).getOwnedCharacter(id, userId);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void updateCustomization_delegatesToService() {
        UUID id = UUID.randomUUID();
        CharacterCustomizationRequest request = new CharacterCustomizationRequest();
        request.setAvatarId("wolf");
        Character updated = new Character("Hero");
        updated.setAvatarId("wolf");
        when(characterService.updateCustomization(id, userId, "wolf", null)).thenReturn(updated);

        var response = controller.updateCustomization(principal, id, request);

        assertThat(response.getBody().getAvatarId()).isEqualTo("wolf");
    }

    @Test
    void syncActivity_delegatesToService() {
        UUID id = UUID.randomUUID();
        ActivitySyncRequest request = new ActivitySyncRequest();
        request.setSource("manual");
        ActivitySyncResponse expected = new ActivitySyncResponse(
                CharacterSheetResponse.from(new Character("Hero")), List.of(), List.of());
        when(activitySyncService.syncActivity(id, userId, request)).thenReturn(expected);

        var response = controller.syncActivity(principal, id, request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void deleteCharacter_returnsNoContent() {
        UUID id = UUID.randomUUID();

        var response = controller.deleteCharacter(principal, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(characterService).deleteOwnedCharacter(id, userId);
    }
}
