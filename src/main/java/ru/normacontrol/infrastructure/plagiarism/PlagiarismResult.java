package ru.normacontrol.infrastructure.plagiarism;

import java.util.List;
import java.util.UUID;

public record PlagiarismResult(
        int uniquenessPercent,
        int plagiarismPercent,
        String verdict,
        List<SimilarFragment> suspiciousFragments,
        List<UUID> similarDocumentIds
) {
}
