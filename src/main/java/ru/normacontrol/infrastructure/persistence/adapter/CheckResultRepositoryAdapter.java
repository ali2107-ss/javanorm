package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.ViolationJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for check results.
 */
@Component
@RequiredArgsConstructor
public class CheckResultRepositoryAdapter implements CheckResultRepository {

    private static final String DEFAULT_RULE_SET = "ГОСТ 19.201-78";
    private static final String DEFAULT_RULE_SET_VERSION = "1.0";

    private final CheckResultJpaRepository jpaRepository;

    @Override
    public CheckResult save(CheckResult checkResult) {
        return toDomain(jpaRepository.save(toJpaEntity(checkResult)));
    }

    @Override
    public Optional<CheckResult> findById(UUID id) {
        return jpaRepository.findWithViolationsById(id).map(this::toDomain);
    }

    @Override
    public List<CheckResult> findByDocumentId(UUID documentId) {
        return jpaRepository.findByDocument_Id(documentId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CheckResult> findLatestByDocumentId(UUID documentId) {
        return jpaRepository.findFirstByDocument_IdOrderByCheckedAtDesc(documentId).map(this::toDomain);
    }

    private CheckResultJpaEntity toJpaEntity(CheckResult checkResult) {
        CheckResultJpaEntity entity = CheckResultJpaEntity.builder()
                .id(checkResult.getId())
                .document(DocumentJpaEntity.builder().id(checkResult.getDocumentId()).build())
                .ruleSetName(checkResult.getRuleSetName() != null ? checkResult.getRuleSetName() : DEFAULT_RULE_SET)
                .ruleSetVersion(checkResult.getRuleSetVersion() != null ? checkResult.getRuleSetVersion() : DEFAULT_RULE_SET_VERSION)
                .complianceScore(checkResult.getComplianceScore() != null
                        ? checkResult.getComplianceScore()
                        : checkResult.calculateScore())
                .passed(checkResult.isPassed())
                .reportStoragePath(checkResult.getReportStoragePath())
                .processingTimeMs(checkResult.getProcessingTimeMs())
                .checkedAt(checkResult.getCheckedAt())
                .uniquenessPercent(checkResult.getUniquenessPercent())
                .plagiarismResult(serializePlagiarism(checkResult.getPlagiarismResult()))
                .build();

        entity.setViolations(checkResult.getViolations().stream()
                .map(violation -> ViolationJpaEntity.builder()
                        .id(violation.getId())
                        .ruleCode(violation.getRuleCode())
                        .description(violation.getDescription())
                        .severity(violation.getSeverity())
                        .pageNumber(violation.getPageNumber())
                        .lineNumber(violation.getLineNumber())
                        .suggestion(violation.getSuggestion())
                        .aiSuggestion(violation.getAiSuggestion())
                        .ruleReference(violation.getRuleReference())
                        .checkResult(entity)
                        .build())
                .toList());

        return entity;
    }

    private CheckResult toDomain(CheckResultJpaEntity entity) {
        List<Violation> violations = entity.getViolations().stream()
                .map(violation -> Violation.builder()
                        .id(violation.getId())
                        .ruleCode(violation.getRuleCode())
                        .description(violation.getDescription())
                        .severity(violation.getSeverity())
                        .pageNumber(violation.getPageNumber())
                        .lineNumber(violation.getLineNumber())
                        .suggestion(violation.getSuggestion())
                        .aiSuggestion(violation.getAiSuggestion())
                        .ruleReference(violation.getRuleReference())
                        .build())
                .toList();

        return CheckResult.builder()
                .id(entity.getId())
                .documentId(entity.getDocument().getId())
                .passed(entity.isPassed())
                .totalViolations(violations.size())
                .checkedAt(entity.getCheckedAt())
                .summary("Балл соответствия: " + entity.getComplianceScore())
                .complianceScore(entity.getComplianceScore())
                .ruleSetName(entity.getRuleSetName())
                .ruleSetVersion(entity.getRuleSetVersion())
                .processingTimeMs(entity.getProcessingTimeMs())
                .reportStoragePath(entity.getReportStoragePath())
                .uniquenessPercent(entity.getUniquenessPercent())
                .plagiarismResult(parsePlagiarism(entity.getPlagiarismResult()))
                .violations(violations)
                .build()
                .evaluate();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String serializePlagiarism(ru.normacontrol.infrastructure.plagiarism.PlagiarismResult result) {
        if (result == null) return null;
        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            return null;
        }
    }

    private ru.normacontrol.infrastructure.plagiarism.PlagiarismResult parsePlagiarism(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, ru.normacontrol.infrastructure.plagiarism.PlagiarismResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
