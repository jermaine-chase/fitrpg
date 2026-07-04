package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.service.GameFormulas;

import java.util.UUID;

/**
 * Read model for a single stat, including the computed XP threshold for the
 * next level so the client can render a progress bar without re-deriving the
 * leveling math.
 */
public class StatResponse {

    private UUID id;
    private String statType;
    private int currentLevel;
    private int currentXp;
    private int xpForNextLevel;
    private String status;

    public static StatResponse from(CharacterStat stat) {
        StatResponse r = new StatResponse();
        r.id = stat.getId();
        r.statType = stat.getStatType().name();
        r.currentLevel = stat.getCurrentLevel();
        r.currentXp = stat.getCurrentXp();
        r.xpForNextLevel = GameFormulas.xpForNextLevel(stat.getCurrentLevel());
        r.status = stat.getStatus();
        return r;
    }

    public UUID getId() {
        return id;
    }

    public String getStatType() {
        return statType;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getCurrentXp() {
        return currentXp;
    }

    public int getXpForNextLevel() {
        return xpForNextLevel;
    }

    public String getStatus() {
        return status;
    }
}
