package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnswerRequest(
        @NotBlank String imageId,
        @NotNull Integer questionIndex,
        @NotBlank String answer
) {}
