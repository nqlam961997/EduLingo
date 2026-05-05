package com.edulingo.dto;

import java.util.List;

public record AssessmentResultResponse(
        int correctCount,
        int totalCount,
        int score,               // 0-100
        String previousLevel,
        String newLevel,
        boolean leveledUp,
        String message,
        List<QuestionResult> results
) {
    public record QuestionResult(
            int questionId,
            String question,
            int chosen,
            int correct,
            boolean isCorrect,
            String explanation
    ) {}
}
