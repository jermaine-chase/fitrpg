package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AdminCharacterUpdateRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterAchievementRepository;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private WorkoutLogRepository workoutLogRepository;
    @Mock
    private CharacterAchievementRepository characterAchievementRepository;

    private CharacterService characterService;

    @BeforeEach
    void setUp() {
        characterService = new CharacterService(characterRepository, workoutLogRepository, characterAchievementRepository);
    }

    @Test
    void createCharacter_seedsAllFourStats() {
        UUID userId = UUID.randomUUID();
        when(characterRepository.save(any(Character.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Character character = characterService.createCharacter("Hero", userId);

        assertThat(character.getCharacterName()).isEqualTo("Hero");
        assertThat(character.getUserId()).isEqualTo(userId);
        assertThat(character.getStats()).hasSize(StatType.values().length);
        assertThat(character.getStats()).allSatisfy(stat -> assertThat(stat.getCharacter()).isEqualTo(character));
    }

    @Test
    void getCharacter_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(characterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.getCharacter(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOwnedCharacter_throwsWhenNotOwnedByUser() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.getOwnedCharacter(id, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOwnedCharacter_returnsCharacterWhenOwned() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Character character = new Character("Hero");
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(character));

        assertThat(characterService.getOwnedCharacter(id, userId)).isSameAs(character);
    }

    @Test
    void updateCharacter_overwritesCoreFields() {
        UUID id = UUID.randomUUID();
        Character character = new Character("Old Name");
        when(characterRepository.findById(id)).thenReturn(Optional.of(character));
        when(characterRepository.save(character)).thenReturn(character);

        AdminCharacterUpdateRequest request = new AdminCharacterUpdateRequest();
        request.setCharacterName("New Name");
        request.setCurrentLevel(5);
        request.setOverallXp(1000);
        request.setStreakCount(3);

        Character updated = characterService.updateCharacter(id, request);

        assertThat(updated.getCharacterName()).isEqualTo("New Name");
        assertThat(updated.getCurrentLevel()).isEqualTo(5);
        assertThat(updated.getOverallXp()).isEqualTo(1000);
        assertThat(updated.getStreakCount()).isEqualTo(3);
    }

    @Test
    void updateCharacter_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(characterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.updateCharacter(id, new AdminCharacterUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCharacter_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(characterRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> characterService.deleteCharacter(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(characterRepository, never()).deleteById(any());
    }

    @Test
    void deleteCharacter_deletesWhenPresent() {
        UUID id = UUID.randomUUID();
        when(characterRepository.existsById(id)).thenReturn(true);

        characterService.deleteCharacter(id);

        verify(characterRepository).deleteById(id);
    }

    @Test
    void deleteOwnedCharacter_throwsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.deleteOwnedCharacter(id, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(characterRepository, never()).delete(any());
    }

    @Test
    void updateCustomization_rejectsUnknownAvatar() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Character character = new Character("Hero");
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(character));

        assertThatThrownBy(() -> characterService.updateCustomization(id, userId, "dragon", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown avatarId");
    }

    @Test
    void updateCustomization_rejectsUnlockedTitleNotEarned() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Character character = new Character("Hero");
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(character));
        when(characterAchievementRepository.existsByCharacterIdAndAchievementCode(id, "FIRST_WORKOUT")).thenReturn(false);

        assertThatThrownBy(() -> characterService.updateCustomization(id, userId, "wolf", "FIRST_WORKOUT"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Achievement not unlocked");
    }

    @Test
    void updateCustomization_succeedsWithValidAvatarAndTitle() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Character character = new Character("Hero");
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(character));
        when(characterAchievementRepository.existsByCharacterIdAndAchievementCode(id, "FIRST_WORKOUT")).thenReturn(true);
        when(characterRepository.save(character)).thenReturn(character);

        Character updated = characterService.updateCustomization(id, userId, "phoenix", "  FIRST_WORKOUT  ");

        assertThat(updated.getAvatarId()).isEqualTo("phoenix");
        assertThat(updated.getTitleAchievementCode()).isEqualTo("FIRST_WORKOUT");
    }

    @Test
    void updateCustomization_blankTitleUnequips() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Character character = new Character("Hero");
        character.setTitleAchievementCode("FIRST_WORKOUT");
        when(characterRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(character));
        when(characterRepository.save(character)).thenReturn(character);

        Character updated = characterService.updateCustomization(id, userId, "wolf", "  ");

        assertThat(updated.getTitleAchievementCode()).isNull();
    }

    @Test
    void listCharacters_delegatesToRepository() {
        List<Character> characters = List.of(new Character("A"), new Character("B"));
        when(characterRepository.findAll()).thenReturn(characters);

        assertThat(characterService.listCharacters()).isEqualTo(characters);
    }

    @Test
    void getMyCharacter_throwsWhenNoneFound() {
        UUID userId = UUID.randomUUID();
        when(characterRepository.findFirstByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.getMyCharacter(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
