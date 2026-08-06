package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.AchievementUnlockDTO;
import com.litrpg.fitness.dto.BonusChallengeDTO;
import com.litrpg.fitness.dto.ClaimRewardResponse;
import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Achievement;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.model.WorkoutLog;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.repository.WorkoutLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core LitRPG business logic: quest rewards, streak pacing, level-ups,
 * level-based XP scaling, and random bonus challenges.
 */
@Service
public class GameEngineService {

    private static final Logger log = LoggerFactory.getLogger(GameEngineService.class);

    private static final double STREAK_BONUS_PER_DAY = 0.05;
    private static final double MAX_STREAK_BONUS     = 0.50;

    /** Probability that a quest claim triggers a random bonus challenge. */
    private static final double BONUS_CHALLENGE_CHANCE = 0.15;

    /** Bonus XP multiplier applied on top of everything else when the roll fires. */
    private static final double BONUS_CHALLENGE_MULTIPLIER = 1.5;

    /**
     * Stat-specific challenge prompts shown to the player when a bonus roll fires.
     * Each entry describes a harder physical variant of the completed quest.
     */
    private static final Map<StatType, List<String>> BONUS_PROMPTS = Map.of(
        StatType.STR, List.of(
            "Add 5–10% more weight to every working set.",
            "Perform one additional set to failure on your main lift.",
            "Reduce rest periods by 30 seconds between every set.",
            "Superset each exercise with a complementary bodyweight movement."
        ),
        StatType.DEX, List.of(
            "Add one extra sprint or interval at the same intensity.",
            "Increase your pace by 15 seconds per kilometre.",
            "Attempt a new skill or movement pattern you have been avoiding.",
            "Complete the session without any scheduled rest — flow state only."
        ),
        StatType.CON, List.of(
            "Extend your distance or duration by 10%.",
            "Complete the final 20% of the workout at maximum sustainable effort.",
            "Remove all planned rest stops.",
            "Add a loaded carry (backpack, weight vest) for at least half the session."
        ),
        StatType.WIL, List.of(
            "Extend your session by 10 minutes with no breaks.",
            "Complete the session in a distracting or uncomfortable environment.",
            "Remove all music, podcasts, or entertainment — silence only.",
            "End with 5 minutes of intentional discomfort (cold shower, ice bath, or breath holds)."
        )
    );

    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final DailyQuestService dailyQuestService;
    private final AchievementService achievementService;

    public GameEngineService(CharacterRepository characterRepository,
                             QuestRepository questRepository,
                             WorkoutLogRepository workoutLogRepository,
                             DailyQuestService dailyQuestService,
                             AchievementService achievementService) {
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.dailyQuestService = dailyQuestService;
        this.achievementService = achievementService;
    }

    public int xpForNextLevel(int currentLevel) {
        return GameFormulas.xpForNextLevel(currentLevel);
    }

    /**
     * Completes a quest for a character.
     *
     * <p>XP pipeline (applied in order):
     * <ol>
     *   <li>Level scale  — {@code baseXp × level^0.75} keeps quests-per-level
     *       roughly constant regardless of character level.</li>
     *   <li>Streak bonus — each consecutive day adds 5 %, capped at +50 %.</li>
     *   <li>Bonus challenge (15 % chance) — an additional 1.5× with a harder
     *       physical prompt returned in the response.</li>
     * </ol>
     *
     * @param characterId the character claiming the rewards
     * @param questId     ID of the quest (must exist in the quests table)
     * @return claim result containing the updated character sheet and any bonus
     * @throws ResourceNotFoundException if the character, quest, or target stat is absent
     */
    @Transactional
    public ClaimRewardResponse claimQuestRewards(UUID characterId, String questId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found: " + characterId));

        Quest quest = questRepository.findById(questId)
                .filter(q -> q.getStatus() == QuestStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Quest not found: " + questId));

        LocalDate today = LocalDate.now();
        boolean alreadyClaimedToday = workoutLogRepository.existsByCharacterIdAndQuestIdAndLoggedAtBetween(
                characterId, questId, today.atStartOfDay(), today.atTime(LocalTime.MAX));
        if (alreadyClaimedToday) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quest '" + questId + "' has already been claimed today");
        }

        // 1. Level-based XP scale (applied to base XP before streak multiplier).
        double levelScale = GameFormulas.questXpScale(character.getCurrentLevel());

        int scaledCharacterXp = (int) Math.round(quest.getBaseCharacterXp() * levelScale);
        int scaledStatXp      = (int) Math.round(quest.getBaseStatXp()      * levelScale);

        // 2. Streak multiplier (computed from streak BEFORE updating it).
        int existingStreak = character.getStreakCount();
        double streakMultiplier = 1.0 + Math.min(existingStreak * STREAK_BONUS_PER_DAY, MAX_STREAK_BONUS);

        int updatedStreak = computeNewStreak(character, today);
        character.setStreakCount(updatedStreak);
        character.setLastWorkoutDate(today);

        int finalCharacterXp = (int) Math.round(scaledCharacterXp * streakMultiplier);
        int finalStatXp      = (int) Math.round(scaledStatXp      * streakMultiplier);

        // 3. Daily Focus bonus — extra reward for claiming today's assigned quest.
        int dailyFocusBonusXp = 0;
        if (dailyQuestService.isTodaysDailyQuest(characterId, questId)) {
            int preDailyTotal = finalCharacterXp + finalStatXp;
            finalCharacterXp  = (int) Math.round(finalCharacterXp * DailyQuestService.DAILY_FOCUS_BONUS_MULTIPLIER);
            finalStatXp       = (int) Math.round(finalStatXp      * DailyQuestService.DAILY_FOCUS_BONUS_MULTIPLIER);
            dailyFocusBonusXp = (finalCharacterXp + finalStatXp) - preDailyTotal;
        }

        // 4. Random bonus challenge.
        BonusChallengeDTO bonusChallenge = null;
        if (ThreadLocalRandom.current().nextDouble() < BONUS_CHALLENGE_CHANCE) {
            List<String> prompts = BONUS_PROMPTS.get(quest.getTargetStat());
            String prompt = prompts.get(ThreadLocalRandom.current().nextInt(prompts.size()));

            int preBonusTotal  = finalCharacterXp + finalStatXp;
            finalCharacterXp   = (int) Math.round(finalCharacterXp * BONUS_CHALLENGE_MULTIPLIER);
            finalStatXp        = (int) Math.round(finalStatXp      * BONUS_CHALLENGE_MULTIPLIER);
            int bonusXp        = (finalCharacterXp + finalStatXp) - preBonusTotal;

            bonusChallenge = new BonusChallengeDTO(prompt, bonusXp);
            log.info("Bonus challenge triggered for character {} on quest '{}': \"{}\" (+{} XP).",
                    characterId, questId, prompt, bonusXp);
        }

        // 5. Apply XP to character and stat.
        applyCharacterXp(character, finalCharacterXp);

        StatType targetStat = quest.getTargetStat();
        CharacterStat stat = character.getStats().stream()
                .filter(s -> s.getStatType() == targetStat)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stat " + targetStat + " not found for character " + characterId));
        applyStatXp(stat, finalStatXp);

        // 6. Persist an immutable workout log (base reflects pre-streak scaled XP).
        WorkoutLog logEntry = new WorkoutLog();
        logEntry.setQuestId(quest.getQuestId());
        logEntry.setQuestTitle(quest.getTitle());
        logEntry.setStatType(targetStat);
        logEntry.setBaseXpEarned(scaledCharacterXp + scaledStatXp);
        logEntry.setMultiplierApplied(BigDecimal.valueOf(streakMultiplier).setScale(2, RoundingMode.HALF_UP));
        logEntry.setFinalXpAwarded(finalCharacterXp + finalStatXp);
        logEntry.setLoggedAt(LocalDateTime.now());
        character.addWorkoutLog(logEntry);

        Character saved = characterRepository.save(character);
        log.info("Character {} (lvl {}) claimed '{}'. Scale={:.2f} streak=x{:.2f} total=+{} XP. Streak now {}.",
                characterId, saved.getCurrentLevel(), questId,
                levelScale, streakMultiplier,
                finalCharacterXp + finalStatXp, updatedStreak);

        // 7. Evaluate badge unlocks against the post-claim state.
        List<Achievement> unlocked = achievementService.evaluateUnlocks(saved);
        List<AchievementUnlockDTO> newAchievements = unlocked.stream()
                .map(AchievementUnlockDTO::from)
                .toList();
        if (!unlocked.isEmpty()) {
            log.info("Character {} unlocked {} achievement(s): {}", characterId, unlocked.size(),
                    unlocked.stream().map(Achievement::getCode).toList());
        }

        return new ClaimRewardResponse(CharacterSheetResponse.from(saved), bonusChallenge, dailyFocusBonusXp,
                newAchievements);
    }

    /**
     * Soft-landing streak rule:
     * <ul>
     *   <li>never trained before → start at 1</li>
     *   <li>already trained today → unchanged (no double-count)</li>
     *   <li>trained yesterday → +1 (consecutive)</li>
     *   <li>missed one or more days, with a streak freeze available → consume
     *       one freeze and preserve the streak unchanged instead of halving it</li>
     *   <li>missed one or more days, no freeze available → halved (floored, min 1)
     *       instead of full reset</li>
     * </ul>
     */
    private int computeNewStreak(Character character, LocalDate today) {
        LocalDate lastWorkoutDate = character.getLastWorkoutDate();
        int existingStreak = character.getStreakCount();
        if (lastWorkoutDate == null) {
            return 1;
        }
        long daysSince = ChronoUnit.DAYS.between(lastWorkoutDate, today);
        if (daysSince <= 0) {
            return Math.max(1, existingStreak);
        }
        if (daysSince == 1) {
            return existingStreak + 1;
        }
        if (character.getStreakFreezeCount() > 0) {
            character.setStreakFreezeCount(character.getStreakFreezeCount() - 1);
            log.info("Character {} used a streak freeze — DAY {} preserved ({} freeze(s) left).",
                    character.getId(), existingStreak, character.getStreakFreezeCount());
            return existingStreak;
        }
        return Math.max(1, existingStreak / 2);
    }

    private void applyCharacterXp(Character character, int xpGained) {
        int xp    = character.getOverallXp() + xpGained;
        int level = character.getCurrentLevel();

        int threshold = xpForNextLevel(level);
        while (xp >= threshold) {
            xp -= threshold;
            level++;
            threshold = xpForNextLevel(level);
        }
        character.setOverallXp(xp);
        character.setCurrentLevel(level);
    }

    private void applyStatXp(CharacterStat stat, int xpGained) {
        if (xpGained > 0 && !"Active".equals(stat.getStatus())) {
            stat.setStatus("Active");
        }
        int xp    = stat.getCurrentXp() + xpGained;
        int level = stat.getCurrentLevel();

        int threshold = xpForNextLevel(level);
        while (xp >= threshold) {
            xp -= threshold;
            level++;
            threshold = xpForNextLevel(level);
        }
        stat.setCurrentXp(xp);
        stat.setCurrentLevel(level);
    }
}
