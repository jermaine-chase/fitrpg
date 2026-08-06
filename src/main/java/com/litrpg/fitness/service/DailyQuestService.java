package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.DailyQuestResponse;
import com.litrpg.fitness.dto.QuestDTO;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.model.DailyQuestAssignment;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.StatType;
import com.litrpg.fitness.repository.CharacterRepository;
import com.litrpg.fitness.repository.DailyQuestAssignmentRepository;
import com.litrpg.fitness.repository.QuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assigns each character a single "Daily Focus" quest per calendar day,
 * biased toward whichever attribute is furthest behind, so the daily
 * challenge nudges players toward well-rounded training rather than
 * whatever they'd claim anyway.
 *
 * <p>Assignment happens lazily on first read of the day (see
 * {@link #getOrAssignDailyQuest}) rather than via a nightly batch job — this
 * keeps a brand-new character's first day working immediately and avoids
 * generating rows for accounts that never log in.
 */
@Service
public class DailyQuestService {

    /** Multiplier applied on top of the normal reward pipeline when the daily quest is claimed. */
    public static final double DAILY_FOCUS_BONUS_MULTIPLIER = 1.25;

    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final DailyQuestAssignmentRepository dailyQuestAssignmentRepository;

    public DailyQuestService(CharacterRepository characterRepository,
                              QuestRepository questRepository,
                              DailyQuestAssignmentRepository dailyQuestAssignmentRepository) {
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.dailyQuestAssignmentRepository = dailyQuestAssignmentRepository;
    }

    @Transactional
    public DailyQuestResponse getOrAssignDailyQuest(UUID characterId) {
        LocalDate today = LocalDate.now();

        DailyQuestAssignment existing = dailyQuestAssignmentRepository
                .findByCharacterIdAndAssignedDate(characterId, today)
                .orElse(null);
        if (existing != null) {
            Quest quest = questRepository.findById(existing.getQuestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quest not found: " + existing.getQuestId()));
            return new DailyQuestResponse(QuestDTO.from(quest), DAILY_FOCUS_BONUS_MULTIPLIER);
        }

        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + characterId));

        List<Quest> unlocked = questRepository.findByMinLevelLessThanEqualAndStatus(
                character.getCurrentLevel(), QuestStatus.APPROVED);
        if (unlocked.isEmpty()) {
            throw new ResourceNotFoundException("No quests available for character " + characterId);
        }

        StatType weakestStat = weakestStat(character);
        List<Quest> targeted = unlocked.stream()
                .filter(q -> q.getTargetStat() == weakestStat)
                .toList();
        List<Quest> candidates = targeted.isEmpty() ? unlocked : targeted;

        Quest chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        dailyQuestAssignmentRepository.save(new DailyQuestAssignment(characterId, chosen.getQuestId(), today));
        return new DailyQuestResponse(QuestDTO.from(chosen), DAILY_FOCUS_BONUS_MULTIPLIER);
    }

    /** The attribute with the lowest level (ties broken randomly). */
    private StatType weakestStat(Character character) {
        int minLevel = character.getStats().stream()
                .mapToInt(CharacterStat::getCurrentLevel)
                .min()
                .orElse(1);
        List<StatType> weakest = character.getStats().stream()
                .filter(s -> s.getCurrentLevel() == minLevel)
                .map(CharacterStat::getStatType)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return weakest.get(ThreadLocalRandom.current().nextInt(weakest.size()));
    }

    /** Whether {@code questId} is the character's already-assigned daily focus quest for today, if any. */
    @Transactional(readOnly = true)
    public boolean isTodaysDailyQuest(UUID characterId, String questId) {
        return dailyQuestAssignmentRepository.findByCharacterIdAndAssignedDate(characterId, LocalDate.now())
                .map(a -> a.getQuestId().equals(questId))
                .orElse(false);
    }
}
