package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quests")
public class Quest {

    @Id
    @Column(name = "quest_id")
    private String questId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_stat", nullable = false, length = 3)
    private StatType targetStat;

    @Column(name = "base_character_xp", nullable = false)
    private int baseCharacterXp;

    @Column(name = "base_stat_xp", nullable = false)
    private int baseStatXp;

    @Column(name = "min_level", nullable = false)
    private int minLevel = 1;

    public Quest() {
    }

    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

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
}
