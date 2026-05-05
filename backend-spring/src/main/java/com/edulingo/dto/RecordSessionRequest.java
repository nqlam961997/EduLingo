package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordSessionRequest(
        @NotBlank String sessionType,   // CHAT | PICTURE
        @NotBlank String topicId,
        String topicName,
        Integer score
) {}
