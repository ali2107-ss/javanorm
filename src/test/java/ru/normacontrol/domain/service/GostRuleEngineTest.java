package ru.normacontrol.domain.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import ru.normacontrol.domain.checker.strategy.CheckStrategy;
import ru.normacontrol.domain.checker.strategy.CheckStrategySettingsService;
import ru.normacontrol.domain.checker.strategy.FormattingCheckStrategy;
import ru.normacontrol.domain.checker.strategy.LanguageCheckStrategy;
import ru.normacontrol.domain.checker.strategy.StructureCheckStrategy;
import ru.normacontrol.domain.checker.strategy.TablesCheckStrategy;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GostRuleEngineTest {

    @Test
    void testStructure_missingAllSections() {
        CheckResult result = engineFor(new StructureCheckStrategy(enabledSettings()))
                .check(new XWPFDocument(), UUID.randomUUID(), UUID.randomUUID());

        long criticalCount = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                .count();

        assertEquals(7, criticalCount);
    }

    @Test
    void testStructure_allSectionsPresent() {
        XWPFDocument document = new XWPFDocument();
        addParagraph(document, "Введение");
        addParagraph(document, "Основания для разработки");
        addParagraph(document, "Назначение разработки");
        addParagraph(document, "Требования к программному изделию");
        addParagraph(document, "Требования к программной документации");
        addParagraph(document, "Стадии и этапы разработки");
        addParagraph(document, "Порядок контроля и приемки");
        addParagraph(document, "Функциональные характеристики");
        addParagraph(document, "Требования к надежности");
        addParagraph(document, "Условия эксплуатации");
        addParagraph(document, "Состав и параметры технических средств");
        addParagraph(document, "Информационная и программная совместимость");

        CheckResult result = engineFor(new StructureCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(0, result.getViolations().size());
    }

    @Test
    void testFormatting_wrongFont_Calibri() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = paragraph.createRun();
        run.setText("Текст документа");
        run.setFontFamily("Calibri");
        run.setFontSize(14);

        CheckResult result = engineFor(new FormattingCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertTrue(result.getViolations().stream().anyMatch(v ->
                v.getSeverity() == ViolationSeverity.WARNING
                        && v.getRuleCode().contains("FORMAT.WRONG_FONT")));
    }

    @Test
    void testFormatting_correctFont_TimesNewRoman() {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = paragraph.createRun();
        run.setText("Текст документа");
        run.setFontFamily("Times New Roman");
        run.setFontSize(14);

        CheckResult result = engineFor(new FormattingCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertFalse(result.getViolations().stream().anyMatch(v -> v.getRuleCode().contains("FORMAT.WRONG_FONT")));
    }

    @Test
    void testLanguage_forbiddenPhrase_itd() {
        XWPFDocument document = new XWPFDocument();
        addParagraph(document, "В документе описаны интерфейсы и т.д.");

        CheckResult result = engineFor(new LanguageCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertTrue(result.getViolations().stream().anyMatch(v ->
                v.getSeverity() == ViolationSeverity.CRITICAL
                        && v.getRuleCode().contains("LANGUAGE.FORBIDDEN_PHRASE")));
    }

    @Test
    void testLanguage_pastTense_found() {
        XWPFDocument document = new XWPFDocument();
        addParagraph(document, "Система использовала внешний сервис");

        CheckResult result = engineFor(new LanguageCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertTrue(result.getViolations().stream().anyMatch(v ->
                v.getSeverity() == ViolationSeverity.WARNING
                        && v.getRuleCode().contains("LANGUAGE.PAST_TENSE")));
    }

    @Test
    void testTables_missingCaption() {
        XWPFDocument document = new XWPFDocument();
        document.createTable(2, 2);

        CheckResult result = engineFor(new TablesCheckStrategy(enabledSettings()))
                .check(document, UUID.randomUUID(), UUID.randomUUID());

        assertTrue(result.getViolations().stream().anyMatch(v ->
                v.getSeverity() == ViolationSeverity.WARNING
                        && v.getRuleCode().contains("TABLE.MISSING_CAPTION")));
    }

    @Test
    void testScore_tenCritical_scoreZero() {
        CheckResult result = CheckResult.builder()
                .violations(new ArrayList<>())
                .build();

        for (int i = 0; i < 10; i++) {
            result.addViolation(criticalViolation("CRITICAL-" + i));
        }

        assertEquals(0, result.calculateScore());
    }

    @Test
    void testScore_perfectDocument_score100() {
        CheckResult result = CheckResult.builder()
                .violations(new ArrayList<>())
                .build()
                .evaluate();

        assertEquals(100, result.getComplianceScore());
        assertTrue(result.isPassed());
    }

    @Test
    void testScore_score80_passed() {
        CheckResult result = CheckResult.builder()
                .complianceScore(80)
                .violations(new ArrayList<>())
                .build()
                .evaluate();

        assertTrue(result.isPassed());
    }

    @Test
    void testScore_score79_failed() {
        CheckResult result = CheckResult.builder()
                .complianceScore(79)
                .violations(new ArrayList<>())
                .build()
                .evaluate();

        assertFalse(result.isPassed());
    }

    private static GostRuleEngine engineFor(CheckStrategy... strategies) {
        return new GostRuleEngine(List.of(strategies));
    }

    private static CheckStrategySettingsService enabledSettings() {
        CheckStrategySettingsService settings = mock(CheckStrategySettingsService.class);
        when(settings.isEnabled(anyString())).thenReturn(true);
        return settings;
    }

    private static void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
    }

    private static Violation criticalViolation(String code) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode(code)
                .description("Critical")
                .severity(ViolationSeverity.CRITICAL)
                .pageNumber(0)
                .lineNumber(0)
                .build();
    }
}
