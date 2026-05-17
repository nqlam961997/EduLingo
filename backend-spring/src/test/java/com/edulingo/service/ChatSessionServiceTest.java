package com.edulingo.service;

import com.edulingo.entity.ChatSession;
import com.edulingo.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatSessionService — chat-session-scratchpad / Layer 2")
class ChatSessionServiceTest {

    @Mock ChatSessionRepository sessions;
    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        service = new ChatSessionService(sessions);
    }

    @Test
    @DisplayName("create() persists a new session with learnerId + topicId + startedAt")
    void createPersistsRow() {
        UUID learnerId = UUID.randomUUID();
        when(sessions.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ChatSession created = service.create(learnerId, "restaurant");

        assertThat(created.getLearnerId()).isEqualTo(learnerId);
        assertThat(created.getTopicId()).isEqualTo("restaurant");
        assertThat(created.getStartedAt()).isNotNull();
        verify(sessions).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("requireOwned() returns the session when ownership matches")
    void requireOwnedReturnsWhenOwnedByLearner() {
        UUID learnerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = new ChatSession();
        existing.setId(sessionId);
        existing.setLearnerId(learnerId);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(existing));

        ChatSession result = service.requireOwned(sessionId, learnerId);

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("requireOwned() throws 404 when session is not found")
    void requireOwnedThrowsNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned(sessionId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("requireOwned() throws 403 when learner does not own the session")
    void requireOwnedThrowsForbidden() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = new ChatSession();
        existing.setId(sessionId);
        existing.setLearnerId(UUID.randomUUID());  // someone else
        when(sessions.findById(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requireOwned(sessionId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not owned");
    }

    @Test
    @DisplayName("updateScratchpad() saves the new JSON and updates lastTurnAt")
    void updateScratchpadPersists() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = new ChatSession();
        existing.setId(sessionId);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(existing));

        service.updateScratchpad(sessionId, "{\"main\":\"pasta\"}");

        assertThat(existing.getScratchpadJson()).isEqualTo("{\"main\":\"pasta\"}");
        assertThat(existing.getLastTurnAt()).isNotNull();
        verify(sessions).save(existing);
    }

    @Test
    @DisplayName("touch() updates lastTurnAt and saves")
    void touchUpdatesLastTurnAt() {
        UUID sessionId = UUID.randomUUID();
        ChatSession existing = new ChatSession();
        existing.setId(sessionId);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(existing));

        service.touch(sessionId);

        assertThat(existing.getLastTurnAt()).isNotNull();
        verify(sessions).save(existing);
    }
}
