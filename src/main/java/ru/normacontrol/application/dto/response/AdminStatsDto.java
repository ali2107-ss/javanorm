package ru.normacontrol.application.dto.response;

import java.util.List;

/**
 * Aggregate statistics for admin dashboard endpoints.
 *
 * @param totalDocuments total non-deleted documents
 * @param totalChecks total completed checks
 * @param averageScore average compliance score
 * @param checksToday checks completed today
 * @param activeUsersThisWeek users with login activity during the last week
 * @param topViolations top ten most frequent violations
 */
public record AdminStatsDto(
        long totalDocuments,
        long totalChecks,
        double averageScore,
        long checksToday,
        long activeUsersThisWeek,
        List<ViolationStatDto> topViolations
) {
}
