package com.litrpg.fitness.dto;

import com.litrpg.fitness.model.QuestTag;
import com.litrpg.fitness.model.StatType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for {@code POST /api/quests/submit} — a player-authored quest
 * idea. Always created {@code PENDING}; an admin must approve it (see
 * {@code POST /api/admin/quests/{id}/approve}) before it appears in the
 * public catalog or can be claimed.
 */
public class QuestSubmissionRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private StatType targetStat;

    /** Defaults to a modest reward; admins can adjust via the quest edit form before or after approving. */
    @PositiveOrZero
    private int baseCharacterXp = 50;

    @PositiveOrZero
    private int baseStatXp = 50;

    @Min(1)
    private int minLevel = 1;

    private QuestTag tag;

    @Positive
    private Integer estimatedMinutes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StatType getTargetStat() {
        return targetStat;
    }

    public void setTargetStat(StatType targetStat) {
        this.targetStat = targetStat;
    }

    public int getBaseCharacterXp() {
        return baseCharacterXp;
    }

    public void setBaseCharacterXp(int baseCharacterXp) {
        this.baseCharacterXp = baseCharacterXp;
    }

    public int getBaseStatXp() {
        return baseStatXp;
    }

    public void setBaseStatXp(int baseStatXp) {
        this.baseStatXp = baseStatXp;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public QuestTag getTag() {
        return tag;
    }

    public void setTag(QuestTag tag) {
        this.tag = tag;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }
}
