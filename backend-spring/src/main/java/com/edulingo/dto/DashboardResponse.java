package com.edulingo.dto;

import java.util.List;

public record DashboardResponse(
        String cefrLevel,
        int cefrIndex,
        String learningGoal,
        String memberSince,
        List<ErrorSummary> topErrors,
        int totalErrorTypes,
        String focusTip,
        // Session stats
        long totalSessions,
        long chatSessions,
        long pictureSessions,
        int averageScore,
        int topicsStudied,
        List<SessionSummary> recentSessions
) {
    public record ErrorSummary(String type, int count, String example) {}

    public record SessionSummary(
            String sessionType,
            String topicName,
            Integer score,
            String date   // dd/MM HH:mm
    ) {}
}
