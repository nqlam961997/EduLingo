package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request payload for POST /api/chat/reply.
 *
 * v1.1 (chat-context-awareness Phase 2): added optional {@code sessionId}.
 * When present, the server validates ownership and threads the persisted
 * scratchpad into the prompt. When absent, the request is processed as a
 * stateless turn for backward compatibility with pre-Phase-2 clients —
 * a stricter "required" enforcement is planned once all clients are upgraded.
 */
public record ChatRequest(
        @NotBlank String topicId,
        String scenario,
        List<MessageItem> history,
        @NotBlank String message,
        String sessionId
) {
    /** Backward-compat constructor — passes through with sessionId=null. */
    public ChatRequest(String topicId, String scenario, List<MessageItem> history, String message) {
        this(topicId, scenario, history, message, null);
    }

    public record MessageItem(String role, String content) {}
}
