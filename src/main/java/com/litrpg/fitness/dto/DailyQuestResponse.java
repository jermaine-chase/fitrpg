package com.litrpg.fitness.dto;

/**
 * A character's "Daily Focus" quest for today, plus the bonus multiplier
 * applied on top of the normal reward pipeline when it is claimed.
 */
public class DailyQuestResponse {

    private QuestDTO quest;
    private double bonusMultiplier;

    public DailyQuestResponse(QuestDTO quest, double bonusMultiplier) {
        this.quest = quest;
        this.bonusMultiplier = bonusMultiplier;
    }

    public QuestDTO getQuest() {
        return quest;
    }

    public double getBonusMultiplier() {
        return bonusMultiplier;
    }
}
