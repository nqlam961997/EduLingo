package com.edulingo.dto;

import java.util.List;

public record AssessmentStartResponse(
        String currentLevel,
        String targetLevel,
        String description,
        List<AssessmentQuestion> questions
) {}
