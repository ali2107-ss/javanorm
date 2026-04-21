package ru.normacontrol.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Движок проверки документов на соответствие ГОСТ 19.201-78
 * «Техническое задание. Требования к содержанию и оформлению».
 *
 * <p>Использует паттерн <b>Chain of Responsibility</b>: каждая группа проверок
 * реализована как отдельный {@link CheckRule}. Движок собирает все
 * зарегистрированные правила через Spring DI, сортирует по приоритету
 * и вызывает последовательно, агрегируя нарушения в {@link CheckResult}.</p>
 *
 * <h3>Группы проверок:</h3>
 * <ol>
 *   <li><b>STRUCT</b> — структура (обязательные разделы)</li>
 *   <li><b>FMT</b> — форматирование (шрифт, кегль, выравнивание)</li>
 *   <li><b>TBL</b> — таблицы (подписи, нумерация, ссылки)</li>
 *   <li><b>FIG</b> — рисунки (подписи, нумерация, ссылки)</li>
 *   <li><b>LANG</b> — язык и стиль (сокращения, время, жаргон)</li>
 * </ol>
 *
 * @see CheckRule
 * @see ru.normacontrol.domain.service.rules.StructureCheckRule
 * @see ru.normacontrol.domain.service.rules.FormattingCheckRule
 * @see ru.normacontrol.domain.service.rules.TableCheckRule
 * @see ru.normacontrol.domain.service.rules.FigureCheckRule
 * @see ru.normacontrol.domain.service.rules.LanguageCheckRule
 */
@Slf4j
@Component
public class GostRuleEngine {

    /** Отсортированная цепочка правил проверки. */
    private final List<CheckRule> ruleChain;

    /**
     * Конструктор — Spring автоматически инжектирует все бины {@link CheckRule}.
     *
     * @param rules список всех зарегистрированных правил проверки
     */
    public GostRuleEngine(List<CheckRule> rules) {
        this.ruleChain = rules.stream()
                .sorted(Comparator.comparingInt(CheckRule::getOrder))
                .collect(Collectors.toList());

        log.info("GostRuleEngine инициализирован. Правил в цепочке: {}", ruleChain.size());
        ruleChain.forEach(r -> log.info("  [{}] {} (order={})",
                r.getClass().getSimpleName(), r.getRuleName(), r.getOrder()));
    }

    /**
     * Выполнить полную проверку DOCX-документа на соответствие ГОСТ 19.201-78.
     *
     * <p>Последовательно вызывает все правила из цепочки, собирает нарушения
     * и формирует итоговый {@link CheckResult}.</p>
     *
     * @param document   Apache POI XWPFDocument для анализа
     * @param documentId UUID документа в системе
     * @param checkedBy  UUID пользователя, инициировавшего проверку
     * @return результат проверки со списком всех обнаруженных нарушений
     */
    public CheckResult check(XWPFDocument document, UUID documentId, UUID checkedBy) {
        log.info("═══ Запуск проверки ГОСТ 19.201-78 для документа {} ═══", documentId);
        long startTime = System.currentTimeMillis();

        CheckResult result = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .checkedAt(LocalDateTime.now())
                .checkedBy(checkedBy)
                .violations(new ArrayList<>())
                .build();

        // ── Последовательный вызов всех правил (Chain of Responsibility) ──
        for (CheckRule rule : ruleChain) {
            log.info("── Выполняется: {} ──", rule.getRuleName());
            try {
                List<Violation> violations = rule.check(document);
                violations.forEach(result::addViolation);
                log.info("   {} → {} нарушений", rule.getRuleName(), violations.size());
            } catch (Exception e) {
                log.error("Ошибка в правиле {}: {}", rule.getRuleName(), e.getMessage(), e);
                result.addViolation(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("ENGINE-ERR")
                        .description("Ошибка при выполнении проверки «"
                                + rule.getRuleName() + "»: " + e.getMessage())
                        .severity(ViolationSeverity.INFO)
                        .pageNumber(0).lineNumber(0)
                        .suggestion("Повторите проверку или обратитесь к администратору")
                        .ruleReference("—")
                        .build());
            }
        }

        // ── Итоговая оценка ───────────────────────────────────────────────
        result.evaluate();

        long elapsed = System.currentTimeMillis() - startTime;
        String summary = buildSummary(result, elapsed);
        result.setSummary(summary);

        log.info("═══ Проверка завершена за {} мс. {} ═══", elapsed, summary);
        return result;
    }

    /**
     * Получить список зарегистрированных правил проверки.
     *
     * @return неизменяемый список правил в порядке выполнения
     */
    public List<CheckRule> getRuleChain() {
        return Collections.unmodifiableList(ruleChain);
    }

    /**
     * Получить количество правил в цепочке.
     *
     * @return количество зарегистрированных правил
     */
    public int getRuleCount() {
        return ruleChain.size();
    }

    // ── Формирование сводки ───────────────────────────────────────────────

    private String buildSummary(CheckResult result, long elapsedMs) {
        long critical = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL).count();
        long warnings = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.WARNING).count();
        long info = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.INFO).count();

        String verdict = result.isPassed()
                ? "СООТВЕТСТВУЕТ ГОСТ 19.201-78"
                : "НЕ СООТВЕТСТВУЕТ ГОСТ 19.201-78";

        return String.format("%s | Всего: %d (CRITICAL: %d, WARNING: %d, INFO: %d) | %d мс",
                verdict, result.getTotalViolations(), critical, warnings, info, elapsedMs);
    }
}
