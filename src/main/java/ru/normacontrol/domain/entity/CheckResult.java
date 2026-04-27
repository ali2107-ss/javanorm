package ru.normacontrol.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.event.CheckCompletedEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate holding all violations found during a document check.
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
    private Integer complianceScore;
    private String ruleSetName;
    private String ruleSetVersion;
    private Long processingTimeMs;
    private String reportStoragePath;

    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    /**
     * Add a single violation to the result.
     *
     * @param violation violation to add
     * @throws IllegalArgumentException when violation is {@code null}
     */
    public void addViolation(Violation violation) {
        if (violation == null) {
            throw new IllegalArgumentException("Violation must not be null");
        }
        violations.add(violation);
        totalViolations = violations.size();
    }

    /**
     * Evaluate pass/fail status according to the compliance threshold.
     *
     * @return current aggregate for chaining
     */
    public CheckResult evaluate() {
        totalViolations = violations.size();
        int score = complianceScore != null ? complianceScore : calculateScore();
        complianceScore = score;
        passed = score >= 80;
        return this;
    }

    /**
     * Compute the resulting compliance score.
     *
     * @return score in the range 0..100
     */
    public int calculateScore() {
        long criticalCount = violations.stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                .count();
        long warningCount = violations.stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.WARNING)
                .count();
        int score = Math.max(0, 100 - (int) criticalCount * 10 - (int) warningCount * 2);
        complianceScore = score;
        return score;
    }

    /**
     * Build completion event after the result is attached to the document.
     *
     * @return completion event
     */
    public CheckCompletedEvent attachResult() {
        int score = complianceScore != null ? complianceScore : calculateScore();
        return new CheckCompletedEvent(documentId, score, passed);
    }

    /**
     * Backward-compatible event factory.
     *
     * @return completion event
     */
    public CheckCompletedEvent toCheckCompletedEvent() {
        return attachResult();
    }
}
