package com.edulingo.dto;

public record AnswerFeedbackResponse(
        String feedback,
        String corrected,
        int score,
        boolean isCorrect
) {}
