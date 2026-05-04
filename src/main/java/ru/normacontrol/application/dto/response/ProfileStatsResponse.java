package ru.normacontrol.application.dto.response;

public record ProfileStatsResponse(
        long checkedCount,
        long documentsCount,
        long checksToday,
        long passedChecks,
        long failedChecks,
        double averageScore
) {
}
