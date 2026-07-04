package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.StatType;

/**
 * Read-only quest definition returned by {@code GET /api/quests}.
 */
public class QuestDTO {

    private String questId;
    private String title;
    private String description;
    private StatType targetStat;
    private int baseCharacterXp;
    private int baseStatXp;
    private int minLevel;

    public static QuestDTO from(Quest quest) {
        QuestDTO dto = new QuestDTO();
        dto.questId = quest.getQuestId();
        dto.title = quest.getTitle();
        dto.description = quest.getDescription();
        dto.targetStat = quest.getTargetStat();
        dto.baseCharacterXp = quest.getBaseCharacterXp();
        dto.baseStatXp = quest.getBaseStatXp();
        dto.minLevel = quest.getMinLevel();
        return dto;
    }

    public String getQuestId() {
        return questId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public StatType getTargetStat() {
        return targetStat;
    }

    public int getBaseCharacterXp() {
        return baseCharacterXp;
    }

    public int getBaseStatXp() {
        return baseStatXp;
    }

    public int getMinLevel() {
        return minLevel;
    }
}
