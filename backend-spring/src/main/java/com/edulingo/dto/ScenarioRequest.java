package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

public record ScenarioRequest(@NotBlank String topicId) {}
