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
 * Стратегия проверки языка и стиля документа.
 * <p>
 * Проверяет соответствие текста требованиям к языку ТЗ:
 * <ul>
 *   <li>Запрещённые сокращения: «и т.д.», «и т.п.», «и пр.», «и др.»</li>
 *   <li>Использование прошедшего времени (документ должен быть в настоящем)</li>
 *   <li>Разговорные обороты и жаргон</li>
 * </ul>
 * </p>
 * <p>
 * ТЗ должно быть написано в настоящем времени или повелительном наклонении.
 * Ссылки: ГОСТ 19.201-78 п.1.4; ГОСТ 2.105-95 п.4.2
 * </p>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    /** Запрещённые сокращения. */
    private static final List<ForbiddenPhrase> FORBIDDEN_ABBREVIATIONS = List.of(
            new ForbiddenPhrase("и т.д.", "Перечислите все элементы явно или используйте «и другие»"),
            new ForbiddenPhrase("и т.п.", "Перечислите все элементы явно или используйте «и тому подобное» полностью"),
            new ForbiddenPhrase("и пр.", "Замените на полное перечисление или «и прочие»"),
            new ForbiddenPhrase("и др.", "Замените на полное перечисление или «и другие»"),
            new ForbiddenPhrase("т.к.", "Замените на «так как» или «поскольку»"),
            new ForbiddenPhrase("т.е.", "Замените на «то есть»"),
            new ForbiddenPhrase("напр.", "Замените на «например»"),
            new ForbiddenPhrase("см.", "Замените на «смотри» или перефразируйте ссылку")
    );

    /** Глагольные окончания прошедшего времени (эвристика). */
    private static final Pattern PAST_TENSE_PATTERN = Pattern.compile(
            "\\b(был[аоиь]?|использовал[аоиьс]*|разработал[аоиьс]*|"
                    + "выполнял[аоиьс]*|проводил[аоиьс]*|создал[аоиьс]*|"
                    + "обеспечивал[аоиьс]*|предусматривал[аоиьс]*|"
                    + "содержал[аоиьс]*|определял[аоиьс]*)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Разговорные и жаргонные слова/обороты. */
    private static final List<ForbiddenPhrase> COLLOQUIAL_PHRASES = List.of(
            new ForbiddenPhrase("короче", "Уберите разговорное выражение"),
            new ForbiddenPhrase("в принципе", "Замените на конкретную формулировку"),
            new ForbiddenPhrase("как бы", "Уберите речевой паразит"),
            new ForbiddenPhrase("на самом деле", "Перефразируйте без вводных конструкций"),
            new ForbiddenPhrase("вообще-то", "Уберите разговорное выражение")
    );

    @Override
    public String getCode() {
        return "LANG";
    }

    @Override
    public String getName() {
        return "Проверка языка и стиля";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии LANG на документе");
        List<Violation> violations = new ArrayList<>();

        try {
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText().trim();
                if (text.isEmpty()) continue;

                String textLower = text.toLowerCase();
                int lineNum = i + 1;
                String style = paragraphs.get(i).getStyle();

                // ── Запрещённые сокращения ────────────────────────────────
                for (ForbiddenPhrase phrase : FORBIDDEN_ABBREVIATIONS) {
                    if (textLower.contains(phrase.text)) {
                        violations.add(Violation.builder()
                                .id(UUID.randomUUID())
                                .ruleCode("LANG-001")
                                .description("Обнаружено запрещённое сокращение «" + phrase.text + "»")
                                .severity(ViolationSeverity.WARNING)
                                .pageNumber(0)
                                .lineNumber(lineNum)
                                .suggestion(phrase.suggestion)
                                .ruleReference("ГОСТ 2.105-95 п.4.2.7")
                                .build());
                    }
                }

                // ── Прошедшее время ───────────────────────────────────────
                // Пропускаем заголовки и специальные стили
                if (style == null || !style.toLowerCase().contains("heading")) {
                    Matcher pastMatcher = PAST_TENSE_PATTERN.matcher(text);
                    if (pastMatcher.find()) {
                        violations.add(Violation.builder()
                                .id(UUID.randomUUID())
                                .ruleCode("LANG-002")
                                .description("Обнаружено прошедшее время: «" + pastMatcher.group() + "»")
                                .severity(ViolationSeverity.INFO)
                                .pageNumber(0)
                                .lineNumber(lineNum)
                                .suggestion("ТЗ следует излагать в настоящем времени / повелительном наклонении")
                                .ruleReference("ГОСТ 19.201-78 п.1.4")
                                .build());
                    }
                }

                // ── Разговорные обороты ───────────────────────────────────
                for (ForbiddenPhrase phrase : COLLOQUIAL_PHRASES) {
                    if (textLower.contains(phrase.text)) {
                        violations.add(Violation.builder()
                                .id(UUID.randomUUID())
                                .ruleCode("LANG-003")
                                .description("Разговорный оборот: «" + phrase.text + "»")
                                .severity(ViolationSeverity.WARNING)
                                .pageNumber(0)
                                .lineNumber(lineNum)
                                .suggestion(phrase.suggestion)
                                .ruleReference("ГОСТ 2.105-95 п.4.2")
                                .build());
                    }
                }
            }

            log.info("LanguageCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении LanguageCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("LANG-ERR")
                .description("Ошибка при выполнении проверки языка: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 19.201-78 п.1.4")
                .build();
    }

    /**
     * Запись для хранения запрещённой фразы и рекомендации по исправлению.
     */
    private record ForbiddenPhrase(String text, String suggestion) {}
}
