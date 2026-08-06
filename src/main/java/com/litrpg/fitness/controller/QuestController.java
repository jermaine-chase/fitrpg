package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.QuestDTO;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestTag;
import com.litrpg.fitness.repository.QuestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only REST API for the quest catalog.
 */
@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestRepository questRepository;

    public QuestController(QuestRepository questRepository) {
        this.questRepository = questRepository;
    }

    /**
     * Returns available quests, optionally filtered to those unlocked at a
     * given character level and/or a category chip.
     *
     * <ul>
     *   <li>{@code GET /api/quests} — all quests</li>
     *   <li>{@code GET /api/quests?level=15} — quests with min_level &lt;= 15</li>
     *   <li>{@code GET /api/quests?tag=CARDIO} — quests tagged CARDIO</li>
     *   <li>{@code GET /api/quests?level=15&tag=CARDIO} — both filters combined</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<QuestDTO>> listQuests(
            @RequestParam(name = "level", required = false) Integer level,
            @RequestParam(name = "tag", required = false) QuestTag tag) {

        List<Quest> matched;
        if (level != null && tag != null) {
            matched = questRepository.findByMinLevelLessThanEqualAndTag(level, tag);
        } else if (level != null) {
            matched = questRepository.findByMinLevelLessThanEqual(level);
        } else if (tag != null) {
            matched = questRepository.findByTag(tag);
        } else {
            matched = questRepository.findAll();
        }

        List<QuestDTO> quests = matched.stream()
                .map(QuestDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(quests);
    }
}
