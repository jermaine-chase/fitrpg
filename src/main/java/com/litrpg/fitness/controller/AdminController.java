package com.litrpg.fitness.controller;

import com.litrpg.fitness.dto.AdminCharacterUpdateRequest;
import com.litrpg.fitness.dto.CharacterSheetResponse;
import com.litrpg.fitness.dto.QuestDTO;
import com.litrpg.fitness.dto.QuestFormRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.repository.QuestRepository;
import com.litrpg.fitness.service.CharacterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only REST API. All endpoints require HTTP Basic Auth.
 * Security is enforced by {@link com.litrpg.fitness.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CharacterService characterService;
    private final QuestRepository questRepository;

    public AdminController(CharacterService characterService, QuestRepository questRepository) {
        this.characterService = characterService;
        this.questRepository = questRepository;
    }

    // ---- Characters --------------------------------------------------------

    /** {@code GET /api/admin/characters} */
    @GetMapping("/characters")
    public ResponseEntity<List<CharacterSheetResponse>> listCharacters() {
        List<CharacterSheetResponse> list = characterService.listCharacters().stream()
                .map(CharacterSheetResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** {@code PUT /api/admin/characters/{id}} */
    @PutMapping("/characters/{id}")
    public ResponseEntity<CharacterSheetResponse> updateCharacter(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCharacterUpdateRequest request) {
        return ResponseEntity.ok(
                CharacterSheetResponse.from(characterService.updateCharacter(id, request)));
    }

    /** {@code DELETE /api/admin/characters/{id}} */
    @DeleteMapping("/characters/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable UUID id) {
        characterService.deleteCharacter(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Quests ------------------------------------------------------------

    /** {@code GET /api/admin/quests} */
    @GetMapping("/quests")
    public ResponseEntity<List<QuestDTO>> listQuests() {
        List<QuestDTO> quests = questRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Quest::getMinLevel).thenComparing(Quest::getQuestId))
                .map(QuestDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(quests);
    }

    /** {@code POST /api/admin/quests} */
    @PostMapping("/quests")
    public ResponseEntity<QuestDTO> createQuest(@Valid @RequestBody QuestFormRequest request) {
        if (request.getQuestId() == null || request.getQuestId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questId is required for create");
        }
        if (questRepository.existsById(request.getQuestId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quest ID already exists: " + request.getQuestId());
        }
        Quest quest = toEntity(new Quest(), request);
        quest.setQuestId(request.getQuestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuestDTO.from(questRepository.save(quest)));
    }

    /** {@code PUT /api/admin/quests/{questId}} */
    @PutMapping("/quests/{questId}")
    public ResponseEntity<QuestDTO> updateQuest(
            @PathVariable String questId,
            @Valid @RequestBody QuestFormRequest request) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new ResourceNotFoundException("Quest not found: " + questId));
        toEntity(quest, request);
        return ResponseEntity.ok(QuestDTO.from(questRepository.save(quest)));
    }

    /** {@code DELETE /api/admin/quests/{questId}} */
    @DeleteMapping("/quests/{questId}")
    public ResponseEntity<Void> deleteQuest(@PathVariable String questId) {
        if (!questRepository.existsById(questId)) {
            throw new ResourceNotFoundException("Quest not found: " + questId);
        }
        questRepository.deleteById(questId);
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -----------------------------------------------------------

    private Quest toEntity(Quest quest, QuestFormRequest req) {
        quest.setTitle(req.getTitle());
        quest.setDescription(req.getDescription());
        quest.setTargetStat(req.getTargetStat());
        quest.setBaseCharacterXp(req.getBaseCharacterXp());
        quest.setBaseStatXp(req.getBaseStatXp());
        quest.setMinLevel(req.getMinLevel());
        quest.setTag(req.getTag());
        quest.setEstimatedMinutes(req.getEstimatedMinutes());
        return quest;
    }
}
