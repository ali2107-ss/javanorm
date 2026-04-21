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
 * Правило №5: Проверка языка и стиля документа.
 * <p>
 * Проверяет:
 * <ul>
 *   <li>Запрещённые сокращения: «и т.д.», «и т.п.», «и пр.», «и др.»</li>
 *   <li>Использование прошедшего времени (документ должен быть в настоящем)</li>
 *   <li>Разговорные обороты и жаргон</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class LanguageCheckRule implements CheckRule {

    /** Запрещённые сокращения с указанием замены. */
    private static final List<ForbiddenPhrase> FORBIDDEN_PHRASES = List.of(
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

    /**
     * {@inheritDoc}
     * <p>Проверяет текст документа на запрещённые фразы, прошедшее время и жаргон.</p>
     */
    @Override
    public List<Violation> check(XWPFDocument document) {
        List<Violation> violations = new ArrayList<>();
        List<XWPFParagraph> paragraphs = document.getParagraphs();

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText().trim();
            if (text.isEmpty()) continue;
            String textLower = text.toLowerCase();
            int line = i + 1;

            // ── Запрещённые сокращения ────────────────────────────────────
            for (ForbiddenPhrase fp : FORBIDDEN_PHRASES) {
                if (textLower.contains(fp.phrase)) {
                    violations.add(Violation.builder().id(UUID.randomUUID())
                            .ruleCode("LANG-001")
                            .description("Обнаружено запрещённое сокращение «" + fp.phrase + "»")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0).lineNumber(line)
                            .suggestion(fp.suggestion)
                            .ruleReference("ГОСТ 2.105-95 п.4.2.7")
                            .build());
                }
            }

            // ── Прошедшее время ───────────────────────────────────────────
            Matcher pastMatcher = PAST_TENSE_PATTERN.matcher(text);
            if (pastMatcher.find()) {
                // Пропускаем заголовки и подписи
                String style = paragraphs.get(i).getStyle();
                if (style == null || !style.toLowerCase().contains("heading")) {
                    violations.add(Violation.builder().id(UUID.randomUUID())
                            .ruleCode("LANG-002")
                            .description("Обнаружено прошедшее время: «" + pastMatcher.group() + "»")
                            .severity(ViolationSeverity.INFO)
                            .pageNumber(0).lineNumber(line)
                            .suggestion("ТЗ следует излагать в настоящем времени / повелительном наклонении")
                            .ruleReference("ГОСТ 19.201-78 п.1.4")
                            .build());
                }
            }

            // ── Разговорные обороты ───────────────────────────────────────
            for (ForbiddenPhrase fp : COLLOQUIAL_PHRASES) {
                if (textLower.contains(fp.phrase)) {
                    violations.add(Violation.builder().id(UUID.randomUUID())
                            .ruleCode("LANG-003")
                            .description("Разговорный оборот: «" + fp.phrase + "»")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0).lineNumber(line)
                            .suggestion(fp.suggestion)
                            .ruleReference("ГОСТ 2.105-95 п.4.2")
                            .build());
                }
            }
        }

        log.info("LanguageCheckRule: {} нарушений", violations.size());
        return violations;
    }

    @Override public String getRuleName() { return "Проверка языка и стиля"; }
    @Override public int getOrder() { return 50; }

    private record ForbiddenPhrase(String phrase, String suggestion) {}
}
