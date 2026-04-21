package ru.normacontrol.domain.service.rules;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.service.CheckRule;

import java.util.*;
import java.util.regex.*;

/**
 * Правило №3: Проверка оформления таблиц.
 * Проверяет подписи «Таблица N — Наименование», нумерацию и ссылки в тексте.
 */
@Slf4j
@Component
public class TableCheckRule implements CheckRule {

    private static final Pattern TABLE_CAPTION = Pattern.compile(
            "таблица\\s+(\\d+)\\s*[—–\\-]\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TABLE_REF = Pattern.compile(
            "(?:таблиц[аеуыи]|табл\\.)\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** {@inheritDoc} */
    @Override
    public List<Violation> check(XWPFDocument document) {
        List<Violation> violations = new ArrayList<>();
        List<XWPFTable> tables = document.getTables();
        if (tables.isEmpty()) return violations;

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        Map<Integer, Integer> captions = new LinkedHashMap<>();
        Set<Integer> refs = new HashSet<>();

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText().trim();
            Matcher cm = TABLE_CAPTION.matcher(text);
            if (cm.find()) captions.put(Integer.parseInt(cm.group(1)), i + 1);
            Matcher rm = TABLE_REF.matcher(text);
            while (rm.find()) refs.add(Integer.parseInt(rm.group(1)));
        }

        if (captions.size() < tables.size()) {
            violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("TBL-001")
                    .description(String.format("Обнаружено %d таблиц, но только %d подписей «Таблица N — Наименование»",
                            tables.size(), captions.size()))
                    .severity(ViolationSeverity.CRITICAL).pageNumber(0).lineNumber(0)
                    .suggestion("Каждая таблица должна иметь подпись: «Таблица 1 — Название»")
                    .ruleReference("ГОСТ 2.105-95 п.4.4.1").build());
        }

        List<Integer> nums = new ArrayList<>(captions.keySet());
        for (int i = 0; i < nums.size(); i++) {
            if (nums.get(i) != i + 1) {
                violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("TBL-002")
                        .description("Нарушена последовательность нумерации таблиц")
                        .severity(ViolationSeverity.WARNING).pageNumber(0).lineNumber(captions.get(nums.get(i)))
                        .suggestion("Нумеруйте таблицы последовательно: 1, 2, 3…")
                        .ruleReference("ГОСТ 2.105-95 п.4.4.2").build());
                break;
            }
        }

        for (var e : captions.entrySet()) {
            if (!refs.contains(e.getKey())) {
                violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("TBL-003")
                        .description("Таблица " + e.getKey() + " не имеет ссылки в тексте")
                        .severity(ViolationSeverity.WARNING).pageNumber(0).lineNumber(e.getValue())
                        .suggestion("Добавьте ссылку: «…в таблице " + e.getKey() + "»")
                        .ruleReference("ГОСТ 2.105-95 п.4.4.7").build());
            }
        }

        log.info("TableCheckRule: {} нарушений", violations.size());
        return violations;
    }

    @Override public String getRuleName() { return "Проверка оформления таблиц"; }
    @Override public int getOrder() { return 30; }
}
