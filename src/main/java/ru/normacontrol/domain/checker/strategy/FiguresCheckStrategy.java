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
 * Стратегия проверки оформления рисунков (изображений).
 * <p>
 * Проверяет соответствие изображений требованиям ГОСТ:
 * <ul>
 *   <li>Каждый рисунок должен иметь подпись вида «Рисунок N — Наименование»</li>
 *   <li>Нумерация рисунков должна быть последовательной</li>
 *   <li>На каждый рисунок должна быть ссылка в тексте</li>
 * </ul>
 * </p>
 * <p>
 * Ссылки: ГОСТ 2.105-95 п.4.3
 * </p>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiguresCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    private static final Pattern FIG_CAPTION = Pattern.compile(
            "рисунок\\s+(\\d+)\\s*[—–\\-]\\s*(.+)", 
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FIG_REF = Pattern.compile(
            "(?:рисунк[аеуои]|рис\\.)\\s*(\\d+)", 
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Override
    public String getCode() {
        return "FIG";
    }

    @Override
    public String getName() {
        return "Проверка оформления рисунков";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии FIG на документе");
        List<Violation> violations = new ArrayList<>();

        try {
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
                log.debug("FiguresCheckStrategy: рисунки не обнаружены");
                return violations;
            }

            Map<Integer, Integer> captions = new LinkedHashMap<>();
            Set<Integer> refs = new HashSet<>();

            // Сбор подписей и ссылок на рисунки
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText().trim();
                
                Matcher captionMatcher = FIG_CAPTION.matcher(text);
                if (captionMatcher.find()) {
                    captions.put(Integer.parseInt(captionMatcher.group(1)), i + 1);
                }
                
                Matcher refMatcher = FIG_REF.matcher(text);
                while (refMatcher.find()) {
                    refs.add(Integer.parseInt(refMatcher.group(1)));
                }
            }

            // Проверка наличия подписей
            if (captions.size() < imageCount) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("FIG-001")
                        .description(String.format(
                                "Обнаружено %d рисунков, но только %d подписей «Рисунок N — Наименование»",
                                imageCount, captions.size()))
                        .severity(ViolationSeverity.CRITICAL)
                        .pageNumber(0)
                        .lineNumber(0)
                        .suggestion("Каждый рисунок должен иметь подпись: «Рисунок 1 — Название»")
                        .ruleReference("ГОСТ 2.105-95 п.4.3.1")
                        .build());
            }

            // Проверка последовательности нумерации
            List<Integer> nums = new ArrayList<>(captions.keySet());
            for (int i = 0; i < nums.size(); i++) {
                if (nums.get(i) != i + 1) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("FIG-002")
                            .description("Нарушена последовательность нумерации рисунков")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0)
                            .lineNumber(captions.get(nums.get(i)))
                            .suggestion("Нумеруйте рисунки последовательно: 1, 2, 3…")
                            .ruleReference("ГОСТ 2.105-95 п.4.3.2")
                            .build());
                    break;
                }
            }

            // Проверка наличия ссылок на рисунки
            for (Map.Entry<Integer, Integer> entry : captions.entrySet()) {
                if (!refs.contains(entry.getKey())) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("FIG-003")
                            .description("Рисунок " + entry.getKey() + " не имеет ссылки в тексте")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0)
                            .lineNumber(entry.getValue())
                            .suggestion("Добавьте ссылку: «…на рисунке " + entry.getKey() + "»")
                            .ruleReference("ГОСТ 2.105-95 п.4.3.6")
                            .build());
                }
            }

            log.info("FiguresCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении FiguresCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("FIG-ERR")
                .description("Ошибка при выполнении проверки рисунков: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 2.105-95 п.4.3")
                .build();
    }
}
