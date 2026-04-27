package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.*;

/**
 * Стратегия проверки форматирования документа.
 * <p>
 * Проверяет соответствие документа требованиям к оформлению:
 * <ul>
 *   <li>Шрифт: Times New Roman или Arial</li>
 *   <li>Кегль: 14 пт</li>
 *   <li>Выравнивание основного текста: по ширине (BOTH)</li>
 * </ul>
 * </p>
 * <p>
 * Ссылки: ГОСТ 19.201-78 п.1.3; ГОСТ 2.105-95 п.4.1
 * </p>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormattingCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    private static final Set<String> ALLOWED_FONTS = Set.of(
            "Times New Roman", "times new roman", "Arial", "arial"
    );

    private static final double TARGET_FONT_SIZE = 14.0;
    private static final double FONT_SIZE_TOLERANCE = 0.5;

    @Override
    public String getCode() {
        return "FMT";
    }

    @Override
    public String getName() {
        return "Проверка форматирования";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии FMT на документе");
        List<Violation> violations = new ArrayList<>();

        try {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            int wrongFontCount = 0;
            int wrongSizeCount = 0;
            int wrongAlignCount = 0;

            int firstWrongFontLine = -1;
            String firstWrongFontName = "";
            int firstWrongSizeLine = -1;
            double firstWrongSizeValue = 0;
            int firstWrongAlignLine = -1;

            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                String text = paragraph.getText().trim();

                // Пропускаем пустые параграфы и заголовки
                if (text.isEmpty()) continue;
                String style = paragraph.getStyle();
                if (style != null && (style.toLowerCase().contains("heading") 
                        || style.toLowerCase().contains("toc"))) continue;

                for (XWPFRun run : paragraph.getRuns()) {
                    String runText = run.text();
                    if (runText == null || runText.trim().isEmpty()) continue;

                    // Проверка шрифта
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

                    // Проверка кегля
                    Double fontSize = run.getFontSizeAsDouble();
                    if (fontSize != null && fontSize > 0
                            && Math.abs(fontSize - TARGET_FONT_SIZE) > FONT_SIZE_TOLERANCE) {
                        wrongSizeCount++;
                        if (firstWrongSizeLine < 0) {
                            firstWrongSizeLine = i + 1;
                            firstWrongSizeValue = fontSize;
                        }
                    }

                    break;
                }

                // Проверка выравнивания
                ParagraphAlignment alignment = paragraph.getAlignment();
                if (alignment != null && alignment != ParagraphAlignment.BOTH
                        && alignment != ParagraphAlignment.DISTRIBUTE) {
                    wrongAlignCount++;
                    if (firstWrongAlignLine < 0) {
                        firstWrongAlignLine = i + 1;
                    }
                }
            }

            // Формирование нарушений
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

            if (wrongAlignCount > 0) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("FMT-003")
                        .description(String.format(
                                "Обнаружено %d параграфов с неправильным выравниванием",
                                wrongAlignCount))
                        .severity(ViolationSeverity.WARNING)
                        .pageNumber(0)
                        .lineNumber(firstWrongAlignLine)
                        .suggestion("Выровняйте текст по ширине (BOTH) или распределите (DISTRIBUTE)")
                        .ruleReference("ГОСТ 2.105-95 п.4.1")
                        .build());
            }

            log.info("FormattingCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении FormattingCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("FMT-ERR")
                .description("Ошибка при выполнении проверки форматирования: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 2.105-95")
                .build();
    }
}
