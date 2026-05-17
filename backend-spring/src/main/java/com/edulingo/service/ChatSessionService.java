package com.edulingo.service;

import com.edulingo.entity.ChatSession;
import com.edulingo.repository.ChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-session state lifecycle. Owns chat_session row creation, ownership
 * validation, scratchpad updates, and "last turn at" tracking.
 */
@Service
public class ChatSessionService {

    private final ChatSessionRepository sessions;

    public ChatSessionService(ChatSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public ChatSession create(UUID learnerId, String topicId) {
        ChatSession s = new ChatSession();
        s.setLearnerId(learnerId);
        s.setTopicId(topicId);
        s.setStartedAt(Instant.now());
        return sessions.save(s);
    }

    /**
     * Look up a session by id and verify the learner owns it.
     *
     * @throws ResponseStatusException 404 if not found, 403 if not owned
     */
    @Transactional(readOnly = true)
    public ChatSession requireOwned(UUID sessionId, UUID learnerId) {
        ChatSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chat session not found"));
        if (!s.getLearnerId().equals(learnerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chat session not owned by this learner");
        }
        return s;
    }

    /** Update the scratchpad JSON. Caller is responsible for shape validation. */
    @Transactional
    public void updateScratchpad(UUID sessionId, String json) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setScratchpadJson(json);
            s.setLastTurnAt(Instant.now());
            sessions.save(s);
        });
    }

    /** Update only last_turn_at; called per turn even when extraction is async. */
    @Transactional
    public void touch(UUID sessionId) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setLastTurnAt(Instant.now());
            sessions.save(s);
        });
    }
}
