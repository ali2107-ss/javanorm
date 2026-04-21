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
 * Правило №4: Проверка оформления рисунков.
 * Проверяет подписи «Рисунок N — Наименование», нумерацию и ссылки.
 */
@Slf4j
@Component
public class FigureCheckRule implements CheckRule {

    private static final Pattern FIG_CAPTION = Pattern.compile(
            "рисунок\\s+(\\d+)\\s*[—–\\-]\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FIG_REF = Pattern.compile(
            "(?:рисунк[аеуои]|рис\\.)\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * {@inheritDoc}
     * <p>Проверяет подписи и ссылки на рисунки в документе.</p>
     */
    @Override
    public List<Violation> check(XWPFDocument document) {
        List<Violation> violations = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();

        // Подсчёт изображений в документе
        int imageCount = 0;
        for (XWPFParagraph p : paragraphs) {
            for (XWPFRun run : p.getRuns()) {
                if (run.getEmbeddedPictures() != null) {
                    imageCount += run.getEmbeddedPictures().size();
                }
            }
        }

        if (imageCount == 0) {
            log.info("FigureCheckRule: рисунки не обнаружены, пропуск");
            return violations;
        }

        Map<Integer, Integer> captions = new LinkedHashMap<>();
        Set<Integer> refs = new HashSet<>();

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText().trim();
            Matcher cm = FIG_CAPTION.matcher(text);
            if (cm.find()) captions.put(Integer.parseInt(cm.group(1)), i + 1);
            Matcher rm = FIG_REF.matcher(text);
            while (rm.find()) refs.add(Integer.parseInt(rm.group(1)));
        }

        // Подписи vs количество изображений
        if (captions.size() < imageCount) {
            violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("FIG-001")
                    .description(String.format(
                            "Обнаружено %d рисунков, но только %d подписей «Рисунок N — Наименование»",
                            imageCount, captions.size()))
                    .severity(ViolationSeverity.CRITICAL).pageNumber(0).lineNumber(0)
                    .suggestion("Каждый рисунок должен иметь подпись: «Рисунок 1 — Название»")
                    .ruleReference("ГОСТ 2.105-95 п.4.3.1").build());
        }

        // Последовательность нумерации
        List<Integer> nums = new ArrayList<>(captions.keySet());
        for (int i = 0; i < nums.size(); i++) {
            if (nums.get(i) != i + 1) {
                violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("FIG-002")
                        .description("Нарушена последовательность нумерации рисунков")
                        .severity(ViolationSeverity.WARNING).pageNumber(0).lineNumber(captions.get(nums.get(i)))
                        .suggestion("Нумеруйте рисунки последовательно: 1, 2, 3…")
                        .ruleReference("ГОСТ 2.105-95 п.4.3.2").build());
                break;
            }
        }

        // Ссылки в тексте
        for (var e : captions.entrySet()) {
            if (!refs.contains(e.getKey())) {
                violations.add(Violation.builder().id(UUID.randomUUID()).ruleCode("FIG-003")
                        .description("Рисунок " + e.getKey() + " не имеет ссылки в тексте")
                        .severity(ViolationSeverity.WARNING).pageNumber(0).lineNumber(e.getValue())
                        .suggestion("Добавьте ссылку: «…на рисунке " + e.getKey() + "»")
                        .ruleReference("ГОСТ 2.105-95 п.4.3.6").build());
            }
        }

        log.info("FigureCheckRule: {} нарушений", violations.size());
        return violations;
    }

    @Override public String getRuleName() { return "Проверка оформления рисунков"; }
    @Override public int getOrder() { return 40; }
}
