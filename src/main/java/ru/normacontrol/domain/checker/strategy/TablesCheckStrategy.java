package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.*;
import java.util.regex.*;

/**
 * Стратегия проверки оформления таблиц.
 * <p>
 * Проверяет соответствие таблиц требованиям ГОСТ:
 * <ul>
 *   <li>Каждая таблица должна иметь подпись вида «Таблица N — Наименование»</li>
 *   <li>Нумерация таблиц должна быть последовательной</li>
 *   <li>На каждую таблицу должна быть ссылка в тексте</li>
 * </ul>
 * </p>
 * <p>
 * Ссылки: ГОСТ 2.105-95 п.4.4
 * </p>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TablesCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    private static final Pattern TABLE_CAPTION = Pattern.compile(
            "таблица\\s+(\\d+)\\s*[—–\\-]\\s*(.+)", 
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TABLE_REF = Pattern.compile(
            "(?:таблиц[аеуыи]|табл\\.)\\s*(\\d+)", 
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Override
    public String getCode() {
        return "TBL";
    }

    @Override
    public String getName() {
        return "Проверка оформления таблиц";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии TBL на документе");
        List<Violation> violations = new ArrayList<>();

        try {
            List<XWPFTable> tables = document.getTables();
            if (tables.isEmpty()) {
                log.debug("TablesCheckStrategy: таблицы не найдены");
                return violations;
            }

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            Map<Integer, Integer> captions = new LinkedHashMap<>();
            Set<Integer> refs = new HashSet<>();

            // Сбор подписей и ссылок на таблицы
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText().trim();
                
                Matcher captionMatcher = TABLE_CAPTION.matcher(text);
                if (captionMatcher.find()) {
                    captions.put(Integer.parseInt(captionMatcher.group(1)), i + 1);
                }
                
                Matcher refMatcher = TABLE_REF.matcher(text);
                while (refMatcher.find()) {
                    refs.add(Integer.parseInt(refMatcher.group(1)));
                }
            }

            // Проверка наличия подписей
            if (captions.size() < tables.size()) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("TBL-001")
                        .description(String.format(
                                "Обнаружено %d таблиц, но только %d подписей «Таблица N — Наименование»",
                                tables.size(), captions.size()))
                        .severity(ViolationSeverity.CRITICAL)
                        .pageNumber(0)
                        .lineNumber(0)
                        .suggestion("Каждая таблица должна иметь подпись: «Таблица 1 — Название»")
                        .ruleReference("ГОСТ 2.105-95 п.4.4.1")
                        .build());
            }

            // Проверка последовательности нумерации
            List<Integer> nums = new ArrayList<>(captions.keySet());
            for (int i = 0; i < nums.size(); i++) {
                if (nums.get(i) != i + 1) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("TBL-002")
                            .description("Нарушена последовательность нумерации таблиц")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0)
                            .lineNumber(captions.get(nums.get(i)))
                            .suggestion("Нумеруйте таблицы последовательно: 1, 2, 3…")
                            .ruleReference("ГОСТ 2.105-95 п.4.4.2")
                            .build());
                    break;
                }
            }

            // Проверка наличия ссылок на таблицы
            for (Map.Entry<Integer, Integer> entry : captions.entrySet()) {
                if (!refs.contains(entry.getKey())) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("TBL-003")
                            .description("Таблица " + entry.getKey() + " не имеет ссылки в тексте")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0)
                            .lineNumber(entry.getValue())
                            .suggestion("Добавьте ссылку: «…в таблице " + entry.getKey() + "»")
                            .ruleReference("ГОСТ 2.105-95 п.4.4.7")
                            .build());
                }
            }

            log.info("TablesCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении TablesCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("TBL-ERR")
                .description("Ошибка при выполнении проверки таблиц: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 2.105-95 п.4.4")
                .build();
    }
}
