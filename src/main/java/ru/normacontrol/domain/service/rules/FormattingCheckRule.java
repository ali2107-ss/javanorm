package ru.normacontrol.domain.service.rules;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.service.CheckRule;

import java.util.*;

/**
 * Правило №2: Проверка форматирования документа.
 * <p>
 * Проверяет:
 * <ul>
 *   <li>Шрифт: Times New Roman или Arial</li>
 *   <li>Кегль: 14 пт</li>
 *   <li>Выравнивание основного текста: по ширине (BOTH)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class FormattingCheckRule implements CheckRule {

    /** Допустимые шрифты по ГОСТ. */
    private static final Set<String> ALLOWED_FONTS = Set.of(
            "Times New Roman", "times new roman",
            "Arial", "arial"
    );

    /** Целевой размер кегля (пт). */
    private static final double TARGET_FONT_SIZE = 14.0;

    /** Допустимое отклонение кегля (пт). */
    private static final double FONT_SIZE_TOLERANCE = 0.5;

    /** Минимальный процент параграфов с нарушением для формирования замечания. */
    private static final double THRESHOLD_PERCENT = 0.1;

    /**
     * {@inheritDoc}
     * <p>Проверяет шрифт, кегль и выравнивание для каждого параграфа документа.</p>
     */
    @Override
    public List<Violation> check(XWPFDocument document) {
        List<Violation> violations = new ArrayList<>();

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int totalParagraphs = 0;
        int wrongFontCount = 0;
        int wrongSizeCount = 0;
        int wrongAlignCount = 0;

        // Для фиксации первого вхождения нарушения
        int firstWrongFontLine = -1;
        String firstWrongFontName = "";
        int firstWrongSizeLine = -1;
        double firstWrongSizeValue = 0;
        int firstWrongAlignLine = -1;

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String text = paragraph.getText().trim();

            // Пропускаем пустые параграфы и заголовки (у них другие правила)
            if (text.isEmpty()) continue;
            String style = paragraph.getStyle();
            if (style != null && style.toLowerCase().contains("heading")) continue;
            if (style != null && style.toLowerCase().contains("toc")) continue;

            totalParagraphs++;

            for (XWPFRun run : paragraph.getRuns()) {
                String runText = run.text();
                if (runText == null || runText.trim().isEmpty()) continue;

                // ── Проверка шрифта ───────────────────────────────────────
                String fontFamily = run.getFontFamily();
                if (fontFamily != null && !fontFamily.isEmpty()
                        && !ALLOWED_FONTS.contains(fontFamily)
                        && !ALLOWED_FONTS.contains(fontFamily.toLowerCase())) {
                    wrongFontCount++;
                    if (firstWrongFontLine < 0) {
                        firstWrongFontLine = i + 1;
                        firstWrongFontName = fontFamily;
                    }
                }

                // ── Проверка кегля ────────────────────────────────────────
                Double fontSize = run.getFontSizeAsDouble();
                if (fontSize != null && fontSize > 0
                        && Math.abs(fontSize - TARGET_FONT_SIZE) > FONT_SIZE_TOLERANCE) {
                    wrongSizeCount++;
                    if (firstWrongSizeLine < 0) {
                        firstWrongSizeLine = i + 1;
                        firstWrongSizeValue = fontSize;
                    }
                }

                // Проверяем только первый run с данными в параграфе
                break;
            }

            // ── Проверка выравнивания ─────────────────────────────────────
            ParagraphAlignment alignment = paragraph.getAlignment();
            if (alignment != null
                    && alignment != ParagraphAlignment.BOTH
                    && alignment != ParagraphAlignment.DISTRIBUTE) {
                wrongAlignCount++;
                if (firstWrongAlignLine < 0) {
                    firstWrongAlignLine = i + 1;
                }
            }
        }

        // ── Генерация нарушений ───────────────────────────────────────────

        if (wrongFontCount > 0) {
            violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("FMT-001")
                    .description(String.format(
                            "Обнаружен недопустимый шрифт «%s» (%d вхождений). "
                                    + "Допускаются: Times New Roman, Arial",
                            firstWrongFontName, wrongFontCount))
                    .severity(ViolationSeverity.CRITICAL)
                    .pageNumber(0)
                    .lineNumber(firstWrongFontLine)
                    .suggestion("Замените шрифт на Times New Roman (основной) или Arial")
                    .ruleReference("ГОСТ 19.201-78 п.1.3; ГОСТ 2.105-95 п.4.1")
                    .build());
        }

        if (wrongSizeCount > 0) {
            violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("FMT-002")
                    .description(String.format(
                            "Обнаружен кегль %.1f пт вместо 14 пт (%d вхождений)",
                            firstWrongSizeValue, wrongSizeCount))
                    .severity(ViolationSeverity.CRITICAL)
                    .pageNumber(0)
                    .lineNumber(firstWrongSizeLine)
                    .suggestion("Установите размер шрифта 14 пт для основного текста")
                    .ruleReference("ГОСТ 19.201-78 п.1.3; ГОСТ 2.105-95 п.4.1")
                    .build());
        }

        if (wrongAlignCount > 0 && totalParagraphs > 0
                && (double) wrongAlignCount / totalParagraphs > THRESHOLD_PERCENT) {
            violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("FMT-003")
                    .description(String.format(
                            "Выравнивание текста не по ширине (%d из %d параграфов)",
                            wrongAlignCount, totalParagraphs))
                    .severity(ViolationSeverity.WARNING)
                    .pageNumber(0)
                    .lineNumber(firstWrongAlignLine)
                    .suggestion("Установите выравнивание основного текста «по ширине» (Justify)")
                    .ruleReference("ГОСТ 19.201-78 п.1.3; ГОСТ 2.105-95 п.4.1.1")
                    .build());
        }

        log.info("FormattingCheckRule: обнаружено {} нарушений", violations.size());
        return violations;
    }

    /** {@inheritDoc} */
    @Override
    public String getRuleName() {
        return "Проверка форматирования";
    }

    /** {@inheritDoc} */
    @Override
    public int getOrder() {
        return 20;
    }
}
