package ru.normacontrol.infrastructure.plagiarism;

import java.util.UUID;

public record SimilarFragment(
        String originalText,
        String sourceText,
        UUID sourceDocumentId,
        String sourceDocumentName,
        int similarityPercent
) {
}
