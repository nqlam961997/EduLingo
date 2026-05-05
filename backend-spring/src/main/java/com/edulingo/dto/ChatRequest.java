package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank String topicId,
        String scenario,
        List<MessageItem> history,
        @NotBlank String message
) {
    public record MessageItem(String role, String content) {}
}
