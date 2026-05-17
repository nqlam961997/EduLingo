package com.edulingo.dto;

/**
 * Response payload from POST /api/chat/start.
 *
 * v1.1 (chat-context-awareness): added {@code suggestPolicy} so the frontend
 * can hide the suggestion chip strip for asymmetric topics, and {@code sessionId}
 * (additive, may be null until Phase 2 wires session persistence).
 */
public record ScenarioResponse(
        String scenario,
        String openingMessage,
        String characterName,
        String characterRole,
        String characterAvatar,
        String suggestPolicy,
        String sessionId
) {}
