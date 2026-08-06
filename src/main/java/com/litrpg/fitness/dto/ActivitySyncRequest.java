package com.litrpg.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/character/{id}/activity-sync}.
 * {@code date} defaults to today if omitted. {@code source} is a free-text
 * label ("manual", "fitbit", "google_fit", "health_connect", ...) — there is
 * no OAuth/device wiring yet, so callers self-report for now.
 */
public class ActivitySyncRequest {

    @NotBlank
    private String source;

    @PositiveOrZero
    private int steps;

    @PositiveOrZero
    private int activeMinutes;

    private LocalDate date;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public void setActiveMinutes(int activeMinutes) {
        this.activeMinutes = activeMinutes;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}
