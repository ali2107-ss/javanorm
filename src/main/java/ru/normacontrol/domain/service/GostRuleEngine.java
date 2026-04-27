package ru.normacontrol.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.checker.strategy.CheckStrategy;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rule engine that orchestrates document validation strategies.
 */
@Slf4j
@Component
public class GostRuleEngine {

    private final List<CheckStrategy> strategies;

    /**
     * Create engine with all registered strategies.
     *
     * @param strategies strategies injected by Spring
     */
    public GostRuleEngine(List<CheckStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(CheckStrategy::getOrder))
                .toList();
    }

    /**
     * Run the full validation flow for the provided document.
     *
     * @param document document to check
     * @param documentId checked document identifier
     * @param checkedBy user who started the check
     * @return aggregated check result
     */
    public CheckResult check(XWPFDocument document, UUID documentId, UUID checkedBy) {
        CheckResult result = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .checkedAt(LocalDateTime.now())
                .checkedBy(checkedBy)
                .violations(new ArrayList<>())
                .build();

        for (CheckStrategy strategy : strategies) {
            if (!strategy.isEnabled()) {
                log.info("Strategy {} [{}] is disabled", strategy.getName(), strategy.getCode());
                continue;
            }

            try {
                List<Violation> violations = strategy.execute(document);
                violations.forEach(result::addViolation);
                logStrategyResult(strategy, violations);
            } catch (Exception ex) {
                log.error("Strategy {} [{}] failed", strategy.getName(), strategy.getCode(), ex);
                result.addViolation(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode(strategy.getCode() + "-ERR")
                        .description("Ошибка при выполнении стратегии " + strategy.getName() + ": " + ex.getMessage())
                        .severity(ViolationSeverity.INFO)
                        .pageNumber(0)
                        .lineNumber(0)
                        .suggestion("Повторите проверку или обратитесь к администратору")
                        .ruleReference("ГОСТ 19.201-78")
                        .build());
            }
        }

        result.evaluate();
        result.setSummary(buildSummary(result));
        return result;
    }

    private void logStrategyResult(CheckStrategy strategy, List<Violation> violations) {
        String bySeverity = violations.stream()
                .collect(Collectors.groupingBy(v -> v.getSeverity().name(), Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        log.info("Strategy {} [{}] found {} violations{}",
                strategy.getName(),
                strategy.getCode(),
                violations.size(),
                bySeverity.isBlank() ? "" : " (" + bySeverity + ")");
    }

    private String buildSummary(CheckResult result) {
        long critical = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                .count();
        long warnings = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.WARNING)
                .count();
        long info = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.INFO)
                .count();
        return "Всего: " + result.getTotalViolations()
                + ", CRITICAL=" + critical
                + ", WARNING=" + warnings
                + ", INFO=" + info;
    }
}
