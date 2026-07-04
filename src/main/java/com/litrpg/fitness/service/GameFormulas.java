package com.litrpg.fitness.service;

/**
 * Pure, side-effect-free LitRPG math. Kept static so both the game engine and
 * the response DTOs derive thresholds from a single source of truth.
 */
public final class GameFormulas {

    private GameFormulas() {
    }

    /**
     * Polynomial leveling curve:
     * <pre>nextLevelXp = 100 * current_level^1.5</pre>
     * The result is floored to an integer XP threshold.
     *
     * @param
     * currentLevel the level the entity is currently on (>= 1)
     * @return XP required to advance from {@code currentLevel} to the next level
     */
    public static int xpForNextLevel(int currentLevel) {
        int level = Math.max(1, currentLevel);
        return (int) Math.floor(100 * Math.pow(level, 1.5));
    }

    /**
     * Level-based XP scale applied to quest rewards at claim time.
     *
     * <pre>scale = characterLevel^0.75</pre>
     *
     * This keeps the number of quests required to level up roughly constant
     * across all levels (~10–15 quests), because the quest XP grows at the same
     * rate as the denominator of (threshold / questXp):
     *
     * <ul>
     *   <li>Level  1 → 1.0×   base XP</li>
     *   <li>Level 10 → 5.6×   base XP</li>
     *   <li>Level 25 → 11.2×  base XP</li>
     *   <li>Level 50 → 18.8×  base XP  (threshold grows to ~35 k)</li>
     * </ul>
     *
     * @param characterLevel the character's current overall level (>= 1)
     * @return multiplicative scale factor (>= 1.0)
     */
    public static double questXpScale(int characterLevel) {
        return Math.pow(Math.max(1, characterLevel), 0.75);
    }
}
