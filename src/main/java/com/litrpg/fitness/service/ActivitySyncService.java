package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.ActivitySyncRequest;
import com.litrpg.fitness.dto.ActivitySyncResponse;
import com.litrpg.fitness.model.ActivitySyncRecord;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.ActivitySyncRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wearable-integration groundwork: a source-agnostic endpoint that turns
 * steps and active minutes into STR/CON XP through the normal reward
 * pipeline. There is no OAuth/device wiring yet (Fitbit/Google
 * Fit/Health Connect are a documented follow-up) — every caller just
 * self-reports a {@code source} label today.
 */
@Service
public class ActivitySyncService {

    private final ActivitySyncRecordRepository activitySyncRecordRepository;
    private final CharacterService characterService;
    private final GameEngineService gameEngineService;

    private final int stepThreshold;
    private final int stepsPerXpUnit;
    private final int minutesThreshold;
    private final int minutesPerXpUnit;
    private final int xpPerUnit;

    public ActivitySyncService(ActivitySyncRecordRepository activitySyncRecordRepository,
                                CharacterService characterService,
                                GameEngineService gameEngineService,
                                @Value("${app.activity-sync.step-threshold}") int stepThreshold,
                                @Value("${app.activity-sync.steps-per-xp-unit}") int stepsPerXpUnit,
                                @Value("${app.activity-sync.minutes-threshold}") int minutesThreshold,
                                @Value("${app.activity-sync.minutes-per-xp-unit}") int minutesPerXpUnit,
                                @Value("${app.activity-sync.xp-per-unit}") int xpPerUnit) {
        this.activitySyncRecordRepository = activitySyncRecordRepository;
        this.characterService = characterService;
        this.gameEngineService = gameEngineService;
        this.stepThreshold = stepThreshold;
        this.stepsPerXpUnit = stepsPerXpUnit;
        this.minutesThreshold = minutesThreshold;
        this.minutesPerXpUnit = minutesPerXpUnit;
        this.xpPerUnit = xpPerUnit;
    }

    /**
     * Steps above {@code app.activity-sync.step-threshold} convert to CON XP;
     * active minutes above {@code app.activity-sync.minutes-threshold}
     * convert to STR XP — both in units of {@code ...-per-xp-unit}, each unit
     * worth {@code app.activity-sync.xp-per-unit} base XP before the reward
     * pipeline's own scaling/multipliers apply.
     *
     * @throws com.litrpg.fitness.exception.ResourceNotFoundException if the character isn't owned by {@code userId}
     * @throws ResponseStatusException 409 if this (character, source, date) was already synced
     */
    @Transactional
    public ActivitySyncResponse syncActivity(UUID characterId, UUID userId, ActivitySyncRequest request) {
        characterService.getOwnedCharacter(characterId, userId);

        LocalDate activityDate = request.getDate() != null ? request.getDate() : LocalDate.now();
        if (activitySyncRecordRepository.existsByCharacterIdAndSourceAndActivityDate(
                characterId, request.getSource(), activityDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Activity already synced from '" + request.getSource() + "' for " + activityDate);
        }

        int conBaseXp = unitsAbove(request.getSteps(), stepThreshold, stepsPerXpUnit) * xpPerUnit;
        int strBaseXp = unitsAbove(request.getActiveMinutes(), minutesThreshold, minutesPerXpUnit) * xpPerUnit;

        Map<StatType, Integer> baseXpByStat = new LinkedHashMap<>();
        if (conBaseXp > 0) baseXpByStat.put(StatType.CON, conBaseXp);
        if (strBaseXp > 0) baseXpByStat.put(StatType.STR, strBaseXp);

        ActivitySyncResponse response = gameEngineService.applyActivitySyncRewards(
                characterId, baseXpByStat, request.getSource());

        int totalXpAwarded = response.rewards().stream().mapToInt(r -> r.xpAwarded()).sum();
        ActivitySyncRecord record = new ActivitySyncRecord(
                characterId, request.getSource(), activityDate, request.getSteps(), request.getActiveMinutes());
        record.setXpAwarded(totalXpAwarded);
        activitySyncRecordRepository.save(record);

        return response;
    }

    private int unitsAbove(int value, int threshold, int perUnit) {
        return Math.max(0, value - threshold) / perUnit;
    }
}
