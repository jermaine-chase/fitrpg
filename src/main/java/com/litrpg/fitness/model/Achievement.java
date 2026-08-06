package com.litrpg.fitness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A badge definition in the catalog. Maps to the {@code achievements} table.
 * Unlocks are tracked separately per character in {@link CharacterAchievement}.
 */
@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @Column(name = "code")
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "criteria_type", nullable = false, length = 30)
    private AchievementCriteriaType criteriaType;

    @Column(nullable = false)
    private int threshold;

    public Achievement() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public AchievementCriteriaType getCriteriaType() {
        return criteriaType;
    }

    public void setCriteriaType(AchievementCriteriaType criteriaType) {
        this.criteriaType = criteriaType;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
