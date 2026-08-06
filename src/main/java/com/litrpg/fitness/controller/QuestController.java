package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.QuestDTO;
import com.litrpg.fitness.dto.QuestSubmissionRequest;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.model.QuestTag;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.security.UserPrincipal;
import com.litrpg.fitness.service.QuestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only REST API for the quest catalog, plus player quest submissions.
 */
@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestRepository questRepository;
    private final QuestService questService;

    public QuestController(QuestRepository questRepository, QuestService questService) {
        this.questRepository = questRepository;
        this.questService = questService;
    }

    /**
     * Returns APPROVED quests only, optionally filtered to those unlocked at
     * a given character level and/or a category chip. Player-submitted
     * quests awaiting review (or rejected) never appear here.
     *
     * <ul>
     *   <li>{@code GET /api/quests} — all approved quests</li>
     *   <li>{@code GET /api/quests?level=15} — approved quests with min_level &lt;= 15</li>
     *   <li>{@code GET /api/quests?tag=CARDIO} — approved quests tagged CARDIO</li>
     *   <li>{@code GET /api/quests?level=15&tag=CARDIO} — both filters combined</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<QuestDTO>> listQuests(
            @RequestParam(name = "level", required = false) Integer level,
            @RequestParam(name = "tag", required = false) QuestTag tag) {

        List<Quest> matched;
        if (level != null && tag != null) {
            matched = questRepository.findByMinLevelLessThanEqualAndTagAndStatus(level, tag, QuestStatus.APPROVED);
        } else if (level != null) {
            matched = questRepository.findByMinLevelLessThanEqualAndStatus(level, QuestStatus.APPROVED);
        } else if (tag != null) {
            matched = questRepository.findByTagAndStatus(tag, QuestStatus.APPROVED);
        } else {
            matched = questRepository.findByStatus(QuestStatus.APPROVED);
        }

        List<QuestDTO> quests = matched.stream()
                .map(QuestDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(quests);
    }

    /**
     * Submits a player-authored quest idea for admin review. Always created
     * PENDING — invisible to {@code GET /api/quests} and unclaimable until an
     * admin approves it via {@code POST /api/admin/quests/{id}/approve}.
     * {@code POST /api/quests/submit}
     */
    @PostMapping("/submit")
    public ResponseEntity<QuestDTO> submitQuest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody QuestSubmissionRequest request) {
        Quest submitted = questService.submitQuest(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(QuestDTO.from(submitted));
    }
}
