package com.litrpg.fitness.service;

import com.litrpg.fitness.model.Character;
import com.litrpg.fitness.model.CharacterStat;
import com.litrpg.fitness.repository.CharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The Midnight Decay Engine.
 *
 * <p>Runs every night at 00:00 (server time). For every character that did not
 * train today, each of the four stats loses 5% of its current level's required
 * XP. XP never drops below 0 and levels are never reduced. A stat whose XP hits
 * 0 is marked "Rusty".
 */
@Service
public class MidnightDecayService {

    private static final Logger log = LoggerFactory.getLogger(MidnightDecayService.class);

    private static final double DECAY_RATE = 0.05;
    private static final String STATUS_RUSTY = "Rusty";

    private final CharacterRepository characterRepository;

    public MidnightDecayService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /**
     * Cron: second minute hour day-of-month month day-of-week.
     * {@code 0 0 0 * * *} -> every day at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void runMidnightDecay() {
        LocalDate today = LocalDate.now();
        List<Character> inactive = characterRepository.findInactiveCharacters(today);

        log.info("Midnight Decay Engine running for {}: {} inactive character(s).",
                today, inactive.size());

        for (Character character : inactive) {
            for (CharacterStat stat : character.getStats()) {
                applyDecay(stat);
            }
        }
        // Cascade persistence handles the dirty stats; explicit save keeps intent clear.
        characterRepository.saveAll(inactive);

        log.info("Midnight Decay Engine finished. {} character(s) decayed.", inactive.size());
    }

    private void applyDecay(CharacterStat stat) {
        int maxXpForLevel = GameFormulas.xpForNextLevel(stat.getCurrentLevel());
        int decayAmount = (int) Math.floor(maxXpForLevel * DECAY_RATE);

        int newXp = Math.max(0, stat.getCurrentXp() - decayAmount);
        stat.setCurrentXp(newXp);

        // Level is never reduced; it is already clamped at >= 1 by game rules.
        if (newXp == 0) {
            stat.setStatus(STATUS_RUSTY);
        }
    }
}
