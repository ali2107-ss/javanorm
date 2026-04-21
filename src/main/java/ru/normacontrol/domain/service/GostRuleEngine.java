package ru.normacontrol.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Движок проверки документов на соответствие ГОСТ 19.201-78.
 * <p>
 * ГОСТ 19.201-78 «Техническое задание. Требования к содержанию и оформлению»
 * определяет обязательную структуру, разделы и правила оформления ТЗ на
 * разработку программ и программных изделий.
 * </p>
 */
@Slf4j
@Component
public class GostRuleEngine {

    // ── Обязательные разделы по ГОСТ 19.201-78 ───────────────────────────
    private static final List<GostSection> REQUIRED_SECTIONS = List.of(
            new GostSection("GOST-001", "Введение",
                    List.of("введение"), "Раздел «Введение» обязателен по ГОСТ 19.201-78 п.2.1"),
            new GostSection("GOST-002", "Основания для разработки",
                    List.of("основания для разработки", "основание для разработки"),
                    "Раздел «Основания для разработки» обязателен по ГОСТ 19.201-78 п.2.2"),
            new GostSection("GOST-003", "Назначение разработки",
                    List.of("назначение разработки", "назначение"),
                    "Раздел «Назначение разработки» обязателен по ГОСТ 19.201-78 п.2.3"),
            new GostSection("GOST-004", "Требования к программе или программному изделию",
                    List.of("требования к программе", "требования к программному изделию",
                            "требования к программе или программному изделию"),
                    "Раздел «Требования к программе» обязателен по ГОСТ 19.201-78 п.2.4"),
            new GostSection("GOST-005", "Требования к программной документации",
                    List.of("требования к программной документации", "программная документация"),
                    "Раздел «Требования к программной документации» обязателен по ГОСТ 19.201-78 п.2.5"),
            new GostSection("GOST-006", "Технико-экономические показатели",
                    List.of("технико-экономические показатели", "технико-экономические"),
                    "Раздел «Технико-экономические показатели» обязателен по ГОСТ 19.201-78 п.2.6"),
            new GostSection("GOST-007", "Стадии и этапы разработки",
                    List.of("стадии и этапы разработки", "стадии и этапы", "этапы разработки"),
                    "Раздел «Стадии и этапы разработки» обязателен по ГОСТ 19.201-78 п.2.7"),
            new GostSection("GOST-008", "Порядок контроля и приёмки",
                    List.of("порядок контроля и приёмки", "порядок контроля", "контроль и приёмка"),
                    "Раздел «Порядок контроля и приёмки» обязателен по ГОСТ 19.201-78 п.2.8")
    );

    // ── Подразделы раздела "Требования к программе" (п.2.4) ───────────────
    private static final List<GostSection> REQUIREMENTS_SUBSECTIONS = List.of(
            new GostSection("GOST-004-A", "Требования к функциональным характеристикам",
                    List.of("функциональные характеристики", "требования к функциональным характеристикам"),
                    "Подраздел «Функциональные характеристики» обязателен по ГОСТ 19.201-78 п.2.4.1"),
            new GostSection("GOST-004-B", "Требования к надёжности",
                    List.of("требования к надёжности", "надёжность"),
                    "Подраздел «Требования к надёжности» обязателен по ГОСТ 19.201-78 п.2.4.2"),
            new GostSection("GOST-004-C", "Условия эксплуатации",
                    List.of("условия эксплуатации"),
                    "Подраздел «Условия эксплуатации» обязателен по ГОСТ 19.201-78 п.2.4.3"),
            new GostSection("GOST-004-D", "Требования к составу и параметрам технических средств",
                    List.of("требования к составу и параметрам", "технические средства",
                            "состав и параметры технических средств"),
                    "Подраздел «Состав и параметры технических средств» обязателен по ГОСТ 19.201-78 п.2.4.4"),
            new GostSection("GOST-004-E", "Требования к информационной и программной совместимости",
                    List.of("информационная и программная совместимость", "программная совместимость",
                            "информационная совместимость"),
                    "Подраздел «Информационная и программная совместимость» обязателен по ГОСТ 19.201-78 п.2.4.5")
    );

    // ── Паттерны для проверки оформления ──────────────────────────────────
    private static final double MIN_FONT_SIZE_PT = 12.0;
    private static final double MAX_FONT_SIZE_PT = 14.0;
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * Выполнить полную проверку документа на соответствие ГОСТ 19.201-78.
     *
     * @param text         Полный текст документа
     * @param metadata     Метаданные документа (шрифт, размеры, поля и т.д.)
     * @param documentId   ID документа
     * @param checkedBy    ID пользователя, инициировавшего проверку
     * @return Результат проверки со списком нарушений
     */
    public CheckResult check(String text, DocumentMetadata metadata,
                             UUID documentId, UUID checkedBy) {
        log.info("Запуск проверки ГОСТ 19.201-78 для документа {}", documentId);

        CheckResult result = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .checkedAt(LocalDateTime.now())
                .checkedBy(checkedBy)
                .violations(new ArrayList<>())
                .build();

        String lowerText = text.toLowerCase();

        // 1. Проверка обязательных разделов
        checkRequiredSections(lowerText, result);

        // 2. Проверка подразделов раздела "Требования к программе"
        checkRequirementsSubsections(lowerText, result);

        // 3. Проверка оформления (шрифт, поля, нумерация)
        checkFormatting(metadata, result);

        // 4. Проверка титульного листа
        checkTitlePage(lowerText, result);

        // 5. Проверка наличия листа утверждения
        checkApprovalSheet(lowerText, result);

        // 6. Проверка нумерации страниц
        checkPageNumbering(metadata, result);

        // Итоговая оценка
        result.evaluate();

        String summaryText = result.isPassed()
                ? "Документ соответствует ГОСТ 19.201-78. Нарушений: " + result.getTotalViolations()
                : "Документ НЕ соответствует ГОСТ 19.201-78. Нарушений: " + result.getTotalViolations();
        result.setSummary(summaryText);

        log.info("Проверка завершена для документа {}. Результат: {}", documentId, summaryText);
        return result;
    }

    // ── Проверка обязательных разделов ─────────────────────────────────────

    private void checkRequiredSections(String lowerText, CheckResult result) {
        for (GostSection section : REQUIRED_SECTIONS) {
            boolean found = section.keywords().stream()
                    .anyMatch(lowerText::contains);
            if (!found) {
                result.addViolation(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode(section.code())
                        .severity(ViolationSeverity.ERROR)
                        .message("Отсутствует обязательный раздел: «" + section.name() + "»")
                        .location("Структура документа")
                        .suggestion(section.hint())
                        .build());
            }
        }
    }

    // ── Проверка подразделов "Требования к программе" ──────────────────────

    private void checkRequirementsSubsections(String lowerText, CheckResult result) {
        // Проверяем подразделы только если сам раздел присутствует
        boolean hasRequirementsSection = REQUIRED_SECTIONS.get(3).keywords().stream()
                .anyMatch(lowerText::contains);
        if (!hasRequirementsSection) return;

        for (GostSection subsection : REQUIREMENTS_SUBSECTIONS) {
            boolean found = subsection.keywords().stream()
                    .anyMatch(lowerText::contains);
            if (!found) {
                result.addViolation(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode(subsection.code())
                        .severity(ViolationSeverity.WARNING)
                        .message("Отсутствует подраздел: «" + subsection.name() + "»")
                        .location("Раздел «Требования к программе»")
                        .suggestion(subsection.hint())
                        .build());
            }
        }
    }

    // ── Проверка оформления ───────────────────────────────────────────────

    private void checkFormatting(DocumentMetadata metadata, CheckResult result) {
        if (metadata == null) return;

        // Проверка размера шрифта
        if (metadata.fontSize() > 0) {
            if (metadata.fontSize() < MIN_FONT_SIZE_PT || metadata.fontSize() > MAX_FONT_SIZE_PT) {
                result.addViolation(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("GOST-FMT-001")
                        .severity(ViolationSeverity.WARNING)
                        .message(String.format("Размер шрифта %.1f пт не соответствует рекомендации (12–14 пт)",
                                metadata.fontSize()))
                        .location("Оформление документа")
                        .suggestion("Используйте шрифт размером 12–14 пт (Times New Roman)")
                        .build());
            }
        }

        // Проверка полей страницы (мм)
        if (metadata.marginLeft() > 0 && metadata.marginLeft() < 20) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-FMT-002")
                    .severity(ViolationSeverity.WARNING)
                    .message("Левое поле меньше 20 мм")
                    .location("Оформление документа")
                    .suggestion("Левое поле должно быть не менее 20 мм (рекомендуется 30 мм)")
                    .build());
        }

        if (metadata.marginRight() > 0 && metadata.marginRight() < 10) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-FMT-003")
                    .severity(ViolationSeverity.WARNING)
                    .message("Правое поле меньше 10 мм")
                    .location("Оформление документа")
                    .suggestion("Правое поле должно быть не менее 10 мм")
                    .build());
        }

        // Проверка межстрочного интервала
        if (metadata.lineSpacing() > 0 && metadata.lineSpacing() < 1.0) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-FMT-004")
                    .severity(ViolationSeverity.INFO)
                    .message("Межстрочный интервал меньше одинарного")
                    .location("Оформление документа")
                    .suggestion("Рекомендуемый межстрочный интервал: 1.0–1.5")
                    .build());
        }
    }

    // ── Проверка титульного листа ─────────────────────────────────────────

    private void checkTitlePage(String lowerText, CheckResult result) {
        List<String> titleKeywords = List.of(
                "утверждаю", "техническое задание",
                "листов", "лист утверждения"
        );

        long foundCount = titleKeywords.stream()
                .filter(lowerText::contains)
                .count();

        if (foundCount < 2) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-TITLE-001")
                    .severity(ViolationSeverity.WARNING)
                    .message("Титульный лист может не соответствовать требованиям ГОСТ 19.201-78")
                    .location("Титульный лист")
                    .suggestion("Титульный лист должен содержать: наименование, гриф утверждения, " +
                            "обозначение документа, количество листов")
                    .build());
        }
    }

    // ── Проверка листа утверждения ─────────────────────────────────────────

    private void checkApprovalSheet(String lowerText, CheckResult result) {
        if (!lowerText.contains("утверждаю") && !lowerText.contains("утверждён")
                && !lowerText.contains("согласовано")) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-APPR-001")
                    .severity(ViolationSeverity.ERROR)
                    .message("Отсутствует лист утверждения/согласования")
                    .location("Лист утверждения")
                    .suggestion("Документ должен содержать лист утверждения с грифом «УТВЕРЖДАЮ»")
                    .build());
        }
    }

    // ── Проверка нумерации страниц ─────────────────────────────────────────

    private void checkPageNumbering(DocumentMetadata metadata, CheckResult result) {
        if (metadata == null) return;
        if (metadata.pageCount() > 1 && !metadata.hasPageNumbers()) {
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("GOST-PAGE-001")
                    .severity(ViolationSeverity.WARNING)
                    .message("Не обнаружена нумерация страниц")
                    .location("Нумерация страниц")
                    .suggestion("Все листы документа должны быть пронумерованы")
                    .build());
        }
    }

    // ── Вспомогательные записи ────────────────────────────────────────────

    /**
     * Описание раздела ГОСТ.
     */
    public record GostSection(
            String code,
            String name,
            List<String> keywords,
            String hint
    ) {}

    /**
     * Метаданные документа для проверки оформления.
     */
    public record DocumentMetadata(
            double fontSize,
            String fontName,
            double marginLeft,
            double marginRight,
            double marginTop,
            double marginBottom,
            double lineSpacing,
            int pageCount,
            boolean hasPageNumbers
    ) {
        public static DocumentMetadata empty() {
            return new DocumentMetadata(0, "", 0, 0, 0, 0, 0, 0, false);
        }
    }
}
