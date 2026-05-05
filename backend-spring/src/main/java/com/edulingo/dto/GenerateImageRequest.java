package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateImageRequest(@NotBlank String topicId) {}
