package ru.normacontrol.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.checker.strategy.CheckStrategy;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.infrastructure.parser.ParsedSection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rule engine that orchestrates document validation strategies.
 * <p>
 * Поддерживает:
 * <ul>
 *   <li>{@link #check(XWPFDocument, UUID, UUID)} — полная проверка с сохранением мета-данных</li>
 *   <li>{@link #createXwpfFromText(String)} — создание XWPFDocument из plain-текста (для PDF-потока)</li>
 * </ul>
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
        log.info("GostRuleEngine инициализирован. Стратегии ({}):", this.strategies.size());
        this.strategies.forEach(s ->
                log.info("  → [{}] {} (порядок={})", s.getCode(), s.getName(), s.getOrder()));
    }

    /**
     * Run the full validation flow for the provided document.
     *
     * @param document   document to check
     * @param documentId checked document identifier
     * @param checkedBy  user who started the check
     * @return aggregated check result — никогда не бросает исключение
     */
    public CheckResult check(XWPFDocument document, UUID documentId, UUID checkedBy) {
        CheckResult result = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .checkedAt(LocalDateTime.now())
                .checkedBy(checkedBy)
                .violations(new ArrayList<>())
                .build();

        if (document == null) {
            log.warn("GostRuleEngine.check() вызван с null-документом — возвращаем пустой результат");
            result.evaluate();
            result.setSummary("Документ не загружен");
            return result;
        }

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        log.info("GostRuleEngine: начало проверки. Параграфов в документе: {}", paragraphs.size());

        for (CheckStrategy strategy : strategies) {
            if (!strategy.isEnabled()) {
                log.info("Стратегия {} [{}] отключена — пропускаем", strategy.getName(), strategy.getCode());
                continue;
            }

            try {
                log.debug("Запуск стратегии {} [{}]", strategy.getName(), strategy.getCode());
                List<Violation> violations = strategy.execute(document);
                if (violations == null) {
                    log.warn("Стратегия {} [{}] вернула null вместо списка", strategy.getName(), strategy.getCode());
                    continue;
                }
                violations.forEach(result::addViolation);
                logStrategyResult(strategy, violations);
            } catch (Exception ex) {
                log.error("Стратегия {} [{}] завершилась с ошибкой: {}",
                        strategy.getName(), strategy.getCode(), ex.getMessage(), ex);
                // Стратегия никогда не должна прерывать проверку — добавляем INFO-нарушение
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

        log.info("GostRuleEngine: проверка завершена. Score={}, violations={}, passed={}",
                result.getComplianceScore(), result.getTotalViolations(), result.isPassed());
        return result;
    }

    /**
     * Создать {@link XWPFDocument} из plain-текста.
     * <p>
     * Используется при конвертации PDF → XWPFDocument:
     * каждая строка текста становится отдельным параграфом.
     * </p>
     *
     * @param text извлечённый текст (например, из PDFBox)
     * @return XWPFDocument с параграфами из текста
     */
    public XWPFDocument createXwpfFromText(String text) {
        if (text == null || text.isBlank()) {
            log.warn("createXwpfFromText: пустой текст — создаём пустой XWPFDocument");
            return new XWPFDocument();
        }

        XWPFDocument doc = new XWPFDocument();
        String[] lines = text.split("\\r?\\n");
        int added = 0;

        for (String line : lines) {
            if (line.isBlank()) {
                continue; // пропускаем пустые строки
            }
            String trimmed = line.trim();
            org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = doc.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
            run.setText(trimmed);

            // Помечаем строки, похожие на заголовки, стилем Heading1
            // (полностью заглавные или начинающиеся с цифры и точки типа "1. Введение")
            boolean isHeading = trimmed.equals(trimmed.toUpperCase()) && trimmed.length() > 3
                    || trimmed.matches("^\\d+\\.\\s+\\p{Lu}.*");
            if (isHeading) {
                paragraph.setStyle("Heading1");
            }

            added++;
        }

        log.info("createXwpfFromText: создан XWPFDocument из {} параграфов (из {} строк)", added, lines.length);
        return doc;
    }

    public List<Violation> checkText(String fullText, List<ParsedSection> sections) {
        List<Violation> violations = new ArrayList<>();
        
        // Проверка структуры (простая)
        List<String> required = List.of(
            "введение", "назначение", "основания"
        );
        String lowerFull = fullText.toLowerCase();
        
        for (String req : required) {
            if (!lowerFull.contains(req)) {
                violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-TEXT-001")
                    .description("Отсутствует обязательный раздел: " + req)
                    .severity(ViolationSeverity.CRITICAL)
                    .build());
            }
        }
        
        // Проверка слов-паразитов
        List<String> forbidden = List.of("и т.д.", "и т.п.", "короче");
        for (String bad : forbidden) {
            if (lowerFull.contains(bad)) {
                violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-TEXT-002")
                    .description("Запрещённое выражение: " + bad)
                    .severity(ViolationSeverity.WARNING)
                    .build());
            }
        }
        
        return violations;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void logStrategyResult(CheckStrategy strategy, List<Violation> violations) {
        String bySeverity = violations.stream()
                .collect(Collectors.groupingBy(v -> v.getSeverity().name(), Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        log.info("Стратегия {} [{}]: найдено {} нарушений{}",
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
        return "Всего нарушений: " + result.getTotalViolations()
                + " | CRITICAL=" + critical
                + " | WARNING=" + warnings
                + " | INFO=" + info
                + " | Оценка=" + result.getComplianceScore() + "/100";
    }
}
