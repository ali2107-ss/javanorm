package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.ViolationJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Адаптер, реализующий доменный порт CheckResultRepository
 * через Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class CheckResultRepositoryAdapter implements CheckResultRepository {

    private final CheckResultJpaRepository jpaRepository;

    @Override
    public CheckResult save(CheckResult checkResult) {
        CheckResultJpaEntity entity = toJpaEntity(checkResult);
        CheckResultJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<CheckResult> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<CheckResult> findByDocumentId(UUID documentId) {
        return jpaRepository.findByDocumentId(documentId).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<CheckResult> findLatestByDocumentId(UUID documentId) {
        return jpaRepository.findFirstByDocumentIdOrderByCheckedAtDesc(documentId).map(this::toDomain);
    }

    private CheckResultJpaEntity toJpaEntity(CheckResult cr) {
        CheckResultJpaEntity entity = CheckResultJpaEntity.builder()
                .id(cr.getId()).documentId(cr.getDocumentId()).passed(cr.isPassed())
                .totalViolations(cr.getTotalViolations()).checkedAt(cr.getCheckedAt())
                .checkedBy(cr.getCheckedBy()).summary(cr.getSummary()).build();

        List<ViolationJpaEntity> violationEntities = cr.getViolations().stream()
                .map(v -> ViolationJpaEntity.builder()
                        .id(v.getId()).ruleCode(v.getRuleCode())
                        .description(v.getDescription()).severity(v.getSeverity())
                        .pageNumber(v.getPageNumber()).lineNumber(v.getLineNumber())
                        .suggestion(v.getSuggestion()).aiSuggestion(v.getAiSuggestion())
                        .ruleReference(v.getRuleReference())
                        .checkResult(entity).build())
                .collect(Collectors.toList());

        entity.setViolations(violationEntities);
        return entity;
    }

    private CheckResult toDomain(CheckResultJpaEntity entity) {
        List<Violation> violations = entity.getViolations().stream()
                .map(v -> Violation.builder()
                        .id(v.getId()).ruleCode(v.getRuleCode())
                        .description(v.getDescription()).severity(v.getSeverity())
                        .pageNumber(v.getPageNumber()).lineNumber(v.getLineNumber())
                        .suggestion(v.getSuggestion()).aiSuggestion(v.getAiSuggestion())
                        .ruleReference(v.getRuleReference())
                        .build())
                .collect(Collectors.toList());

        return CheckResult.builder()
                .id(entity.getId()).documentId(entity.getDocumentId())
                .passed(entity.isPassed()).totalViolations(entity.getTotalViolations())
                .checkedAt(entity.getCheckedAt()).checkedBy(entity.getCheckedBy())
                .summary(entity.getSummary()).violations(violations).build();
    }
}
