package com.edulingo.dto;

import java.util.List;

public record AssessmentQuestion(
        int id,
        String question,
        List<String> options,
        int correct,
        String explanation
) {}
