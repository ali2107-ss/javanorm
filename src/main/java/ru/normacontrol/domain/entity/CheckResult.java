package ru.normacontrol.domain.entity;

import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Доменная сущность результата проверки документа.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {
    private UUID id;
    private UUID documentId;
    private boolean passed;
    private int totalViolations;
    private LocalDateTime checkedAt;
    private UUID checkedBy;
    private String summary;

    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    /**
     * Добавить нарушение к результату проверки.
     *
     * @param violation нарушение для добавления
     */
    public void addViolation(Violation violation) {
        violations.add(violation);
        totalViolations = violations.size();
    }

    /**
     * Определить, прошёл ли документ проверку.
     * Документ НЕ проходит, если есть хотя бы одно CRITICAL нарушение.
     */
    public void evaluate() {
        this.passed = violations.stream()
                .noneMatch(v ->
                        v.getSeverity() == ru.normacontrol.domain.enums.ViolationSeverity.CRITICAL);
        this.totalViolations = violations.size();
    }
}
