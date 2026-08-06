package com.litrpg.fitness.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GameFormulasTest {

    @ParameterizedTest
    @CsvSource({
            "1, 100",
            "2, 282",
            "4, 800",
            "10, 3162"
    })
    void xpForNextLevel_followsPolynomialCurve(int level, int expectedXp) {
        assertThat(GameFormulas.xpForNextLevel(level)).isEqualTo(expectedXp);
    }

    @Test
    void xpForNextLevel_treatsSubOneLevelsAsLevelOne() {
        assertThat(GameFormulas.xpForNextLevel(0)).isEqualTo(GameFormulas.xpForNextLevel(1));
        assertThat(GameFormulas.xpForNextLevel(-5)).isEqualTo(GameFormulas.xpForNextLevel(1));
    }

    @Test
    void questXpScale_isOneAtLevelOne() {
        assertThat(GameFormulas.questXpScale(1)).isEqualTo(1.0);
    }

    @Test
    void questXpScale_increasesWithLevel() {
        double low = GameFormulas.questXpScale(5);
        double high = GameFormulas.questXpScale(25);
        assertThat(high).isGreaterThan(low);
    }

    @Test
    void questXpScale_treatsSubOneLevelsAsLevelOne() {
        assertThat(GameFormulas.questXpScale(0)).isEqualTo(1.0);
        assertThat(GameFormulas.questXpScale(-10)).isEqualTo(1.0);
    }
}
