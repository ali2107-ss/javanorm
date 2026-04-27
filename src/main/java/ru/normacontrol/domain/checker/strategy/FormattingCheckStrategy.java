package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Strategy for formatting validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormattingCheckStrategy implements CheckStrategy {

    private static final Set<String> ALLOWED_FONTS = Set.of("Times New Roman", "Arial");
    private static final double TARGET_FONT_SIZE = 14.0;
    private static final double FONT_SIZE_TOLERANCE = 0.5;

    private final CheckStrategySettingsService settingsService;

    @Override
    public String getCode() {
        return "FMT";
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
        List<Violation> violations = new ArrayList<>();

        for (int i = 0; i < document.getParagraphs().size(); i++) {
            XWPFParagraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getText() == null || paragraph.getText().isBlank()) {
                continue;
            }

            XWPFRun run = paragraph.getRuns().isEmpty() ? null : paragraph.getRuns().get(0);
            if (run == null) {
                continue;
            }

            String fontFamily = run.getFontFamily();
            if (fontFamily != null && !fontFamily.isBlank() && ALLOWED_FONTS.stream().noneMatch(fontFamily::equalsIgnoreCase)) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("FORMAT.WRONG_FONT")
                        .description("Недопустимый шрифт: " + fontFamily)
                        .severity(ViolationSeverity.WARNING)
                        .pageNumber(0)
                        .lineNumber(i + 1)
                        .suggestion("Используйте Times New Roman")
                        .ruleReference("ГОСТ 19.201-78 п.1.3")
                        .build());
            }

            Double fontSize = run.getFontSizeAsDouble();
            if (fontSize != null && Math.abs(fontSize - TARGET_FONT_SIZE) > FONT_SIZE_TOLERANCE) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("FORMAT.WRONG_SIZE")
                        .description("Недопустимый кегль: " + fontSize)
                        .severity(ViolationSeverity.CRITICAL)
                        .pageNumber(0)
                        .lineNumber(i + 1)
                        .suggestion("Используйте 14 pt")
                        .ruleReference("ГОСТ 19.201-78 п.1.3")
                        .build());
            }

            ParagraphAlignment alignment = paragraph.getAlignment();
            if (alignment != null && alignment != ParagraphAlignment.BOTH && alignment != ParagraphAlignment.DISTRIBUTE) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("FORMAT.WRONG_ALIGNMENT")
                        .description("Неверное выравнивание")
                        .severity(ViolationSeverity.WARNING)
                        .pageNumber(0)
                        .lineNumber(i + 1)
                        .suggestion("Используйте выравнивание по ширине")
                        .ruleReference("ГОСТ 2.105-95 п.4.1")
                        .build());
            }
        }

        log.info("FormattingCheckStrategy found {} violations", violations.size());
        return violations;
    }
}
