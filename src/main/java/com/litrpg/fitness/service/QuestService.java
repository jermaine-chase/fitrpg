package com.litrpg.fitness.service;

import com.litrpg.fitness.dto.QuestSubmissionRequest;
import com.litrpg.fitness.exception.ResourceNotFoundException;
import com.litrpg.fitness.model.Quest;
import com.litrpg.fitness.model.QuestStatus;
import com.litrpg.fitness.repository.QuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Player-submitted quest ideas and the admin approval queue they sit in
 * before joining the public catalog. Admin-authored quests (created via
 * {@code AdminController}) bypass this entirely, defaulting straight to
 * {@link QuestStatus#APPROVED}.
 */
@Service
public class QuestService {

    private final QuestRepository questRepository;

    public QuestService(QuestRepository questRepository) {
        this.questRepository = questRepository;
    }

    /** Creates a PENDING quest from a player submission, awaiting admin review. */
    @Transactional
    public Quest submitQuest(UUID submittedByUserId, QuestSubmissionRequest request) {
        Quest quest = new Quest();
        quest.setQuestId(generateQuestId());
        quest.setTitle(request.getTitle());
        quest.setDescription(request.getDescription());
        quest.setTargetStat(request.getTargetStat());
        quest.setBaseCharacterXp(request.getBaseCharacterXp());
        quest.setBaseStatXp(request.getBaseStatXp());
        quest.setMinLevel(request.getMinLevel());
        quest.setTag(request.getTag());
        quest.setEstimatedMinutes(request.getEstimatedMinutes());
        quest.setStatus(QuestStatus.PENDING);
        quest.setCreatedByUserId(submittedByUserId);
        return questRepository.save(quest);
    }

    @Transactional(readOnly = true)
    public List<Quest> listPending() {
        return questRepository.findByStatus(QuestStatus.PENDING);
    }

    @Transactional
    public Quest approve(String questId) {
        return updateStatus(questId, QuestStatus.APPROVED);
    }

    @Transactional
    public Quest reject(String questId) {
        return updateStatus(questId, QuestStatus.REJECTED);
    }

    private Quest updateStatus(String questId, QuestStatus status) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new ResourceNotFoundException("Quest not found: " + questId));
        quest.setStatus(status);
        return questRepository.save(quest);
    }

    private String generateQuestId() {
        return "PQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
