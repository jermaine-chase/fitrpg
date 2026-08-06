package com.litrpg.fitness.service;

import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MidnightDecayServiceTest {

    @Mock
    private CharacterRepository characterRepository;

    private MidnightDecayService midnightDecayService;

    @BeforeEach
    void setUp() {
        midnightDecayService = new MidnightDecayService(characterRepository);
    }

    @Test
    void runMidnightDecay_reducesXpForInactiveCharacters() {
        Character character = new Character("Hero");
        CharacterStat stat = new CharacterStat(StatType.STR);
        stat.setCurrentLevel(1);
        stat.setCurrentXp(50);
        character.addStat(stat);
        when(characterRepository.findInactiveCharacters(any())).thenReturn(List.of(character));

        midnightDecayService.runMidnightDecay();

        int expectedDecay = (int) Math.floor(GameFormulas.xpForNextLevel(1) * 0.05);
        assertThat(stat.getCurrentXp()).isEqualTo(50 - expectedDecay);
        assertThat(stat.getStatus()).isEqualTo("Active");
        verify(characterRepository).saveAll(List.of(character));
    }

    @Test
    void runMidnightDecay_marksStatRustyWhenXpHitsZero() {
        Character character = new Character("Hero");
        CharacterStat stat = new CharacterStat(StatType.STR);
        stat.setCurrentLevel(1);
        stat.setCurrentXp(1);
        character.addStat(stat);
        when(characterRepository.findInactiveCharacters(any())).thenReturn(List.of(character));

        midnightDecayService.runMidnightDecay();

        assertThat(stat.getCurrentXp()).isZero();
        assertThat(stat.getStatus()).isEqualTo("Rusty");
    }

    @Test
    void runMidnightDecay_neverDropsXpBelowZero() {
        Character character = new Character("Hero");
        CharacterStat stat = new CharacterStat(StatType.STR);
        stat.setCurrentLevel(50);
        stat.setCurrentXp(0);
        character.addStat(stat);
        when(characterRepository.findInactiveCharacters(any())).thenReturn(List.of(character));

        midnightDecayService.runMidnightDecay();

        assertThat(stat.getCurrentXp()).isZero();
    }

    @Test
    void runMidnightDecay_noInactiveCharactersSavesEmptyList() {
        when(characterRepository.findInactiveCharacters(any())).thenReturn(List.of());

        midnightDecayService.runMidnightDecay();

        verify(characterRepository).saveAll(List.of());
    }
}
