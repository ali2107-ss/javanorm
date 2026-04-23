package ru.normacontrol.application.dto;

import lombok.Builder;
import ru.normacontrol.domain.entity.Violation;
import java.util.UUID;

@Builder
public record ViolationDto(
        UUID id,
        String ruleCode,
        String description,
        String severity,
        int pageNumber,
        int lineNumber,
        String suggestion,
        String aiSuggestion,
        String ruleReference
) {
    public static ViolationDto from(Violation violation) {
        return ViolationDto.builder()
                .id(violation.getId())
                .ruleCode(violation.getRuleCode())
                .description(violation.getDescription())
                .severity(violation.getSeverity() != null ? violation.getSeverity().name() : null)
                .pageNumber(violation.getPageNumber())
                .lineNumber(violation.getLineNumber())
                .suggestion(violation.getSuggestion())
                .aiSuggestion(violation.getAiSuggestion())
                .ruleReference(violation.getRuleReference())
                .build();
    }
}
