package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.service.GameFormulas;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Character detail shown to a friend, trimmed to what their granted
 * {@link com.litrpg.fitness.model.FriendVisibility} level allows. Never
 * constructed directly for {@code NONE} — see {@code FriendDetailResponse}.
 */
public class FriendCharacterSummary {

    private String characterName;
    private int currentLevel;
    private Integer overallXp;
    private Integer xpForNextLevel;
    private Integer streakCount;
    private List<StatResponse> stats;

    /** BASIC visibility: name and level only. */
    public static FriendCharacterSummary basic(Character character) {
        FriendCharacterSummary r = new FriendCharacterSummary();
        r.characterName = character.getCharacterName();
        r.currentLevel = character.getCurrentLevel();
        return r;
    }

    /** FULL visibility: the complete sheet. */
    public static FriendCharacterSummary full(Character character) {
        FriendCharacterSummary r = basic(character);
        r.overallXp = character.getOverallXp();
        r.xpForNextLevel = GameFormulas.xpForNextLevel(character.getCurrentLevel());
        r.streakCount = character.getStreakCount();
        r.stats = character.getStats().stream()
                .sorted(Comparator.comparing(s -> s.getStatType().name()))
                .map(StatResponse::from)
                .collect(Collectors.toList());
        return r;
    }

    public String getCharacterName() {
        return characterName;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public Integer getOverallXp() {
        return overallXp;
    }

    public Integer getXpForNextLevel() {
        return xpForNextLevel;
    }

    public Integer getStreakCount() {
        return streakCount;
    }

    public List<StatResponse> getStats() {
        return stats;
    }
}
