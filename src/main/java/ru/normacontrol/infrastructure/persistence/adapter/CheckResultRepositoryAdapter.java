package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.ViolationJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                .ruleSetName(DEFAULT_RULE_SET)
                .ruleSetVersion(DEFAULT_RULE_SET_VERSION)
                .complianceScore(checkResult.calculateScore())
                .passed(checkResult.isPassed())
                .reportStoragePath(null)
                .processingTimeMs(null)
                .checkedAt(checkResult.getCheckedAt())
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
                .map(v -> Violation.builder()
                        .id(v.getId())
                        .ruleCode(v.getRuleCode())
                        .description(v.getDescription())
                        .severity(v.getSeverity())
                        .pageNumber(v.getPageNumber())
                        .lineNumber(v.getLineNumber())
                        .suggestion(v.getSuggestion())
                        .aiSuggestion(v.getAiSuggestion())
                        .ruleReference(v.getRuleReference())
                        .build())
                .toList();

        CheckResult result = CheckResult.builder()
                .id(entity.getId())
                .documentId(entity.getDocument().getId())
                .passed(entity.isPassed())
                .totalViolations(violations.size())
                .checkedAt(entity.getCheckedAt())
                .checkedBy(null)
                .summary("Балл соответствия: " + entity.getComplianceScore())
                .violations(violations)
                .build();
        result.evaluate();
        return result;
    }
}
