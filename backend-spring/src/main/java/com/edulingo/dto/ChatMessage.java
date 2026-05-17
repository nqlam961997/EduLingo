package com.edulingo.dto;

/**
 * Alternating-turn chat message sent to an AI provider's chat API.
 * Replaces the previous flat "Learner: ... \n You: ..." pseudo-transcript
 * per the chat-multi-turn-context spec.
 */
public record ChatMessage(MessageRole role, String content) {

    public enum MessageRole {
        USER,
        ASSISTANT
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(MessageRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(MessageRole.ASSISTANT, content);
    }
}
