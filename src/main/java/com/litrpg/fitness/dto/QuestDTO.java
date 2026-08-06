package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.QuestTag;
import com.litrpg.fitness.model.StatType;

import java.util.UUID;

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
    private QuestTag tag;
    private Integer estimatedMinutes;
    private QuestStatus status;
    private UUID createdByUserId;

    public static QuestDTO from(Quest quest) {
        QuestDTO dto = new QuestDTO();
        dto.questId = quest.getQuestId();
        dto.title = quest.getTitle();
        dto.description = quest.getDescription();
        dto.targetStat = quest.getTargetStat();
        dto.baseCharacterXp = quest.getBaseCharacterXp();
        dto.baseStatXp = quest.getBaseStatXp();
        dto.minLevel = quest.getMinLevel();
        dto.tag = quest.getTag();
        dto.estimatedMinutes = quest.getEstimatedMinutes();
        dto.status = quest.getStatus();
        dto.createdByUserId = quest.getCreatedByUserId();
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

    public QuestTag getTag() {
        return tag;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
