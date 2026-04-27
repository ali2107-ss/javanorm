package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.*;

/**
 * Стратегия проверки структуры документа на соответствие ГОСТ 19.201-78.
 * <p>
 * Проверяет наличие всех обязательных разделов и подразделов, указанных
 * в пункте 2 ГОСТ 19.201-78 «Техническое задание. Требования к содержанию
 * и оформлению».
 * </p>
 * <h3>Проверяемые разделы (п.2):</h3>
 * <ul>
 *   <li><b>п.2.1</b> — Введение</li>
 *   <li><b>п.2.2</b> — Основания для разработки</li>
 *   <li><b>п.2.3</b> — Назначение разработки</li>
 *   <li><b>п.2.4</b> — Требования к программному изделию (с подразделами)</li>
 *   <li><b>п.2.5</b> — Требования к программной документации</li>
 *   <li><b>п.2.7</b> — Стадии и этапы разработки</li>
 *   <li><b>п.2.8</b> — Порядок контроля и приёмки</li>
 * </ul>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructureCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    /** Обязательные разделы: код → (название, ключевые слова, ссылка на ГОСТ). */
    private static final List<RequiredSection> REQUIRED_SECTIONS = List.of(
            new RequiredSection("STRUCT-001", "Введение",
                    List.of("введение"),
                    "ГОСТ 19.201-78 п.2.1"),
            new RequiredSection("STRUCT-002", "Основания для разработки",
                    List.of("основания для разработки", "основание для разработки"),
                    "ГОСТ 19.201-78 п.2.2"),
            new RequiredSection("STRUCT-003", "Назначение разработки",
                    List.of("назначение разработки"),
                    "ГОСТ 19.201-78 п.2.3"),
            new RequiredSection("STRUCT-004", "Требования к программному изделию",
                    List.of("требования к программе", "требования к программному изделию",
                            "требования к программе или программному изделию"),
                    "ГОСТ 19.201-78 п.2.4"),
            new RequiredSection("STRUCT-005", "Требования к программной документации",
                    List.of("требования к программной документации",
                            "требования к документации"),
                    "ГОСТ 19.201-78 п.2.5"),
            new RequiredSection("STRUCT-006", "Стадии и этапы разработки",
                    List.of("стадии и этапы разработки", "стадии и этапы",
                            "этапы разработки"),
                    "ГОСТ 19.201-78 п.2.7"),
            new RequiredSection("STRUCT-007", "Порядок контроля и приёмки",
                    List.of("порядок контроля и приёмки", "порядок контроля и приемки",
                            "порядок контроля", "контроль и приёмка", "контроль и приемка"),
                    "ГОСТ 19.201-78 п.2.8")
    );

    /** Подразделы раздела «Требования к программному изделию» (п.2.4). */
    private static final List<RequiredSection> REQUIREMENTS_SUBSECTIONS = List.of(
            new RequiredSection("STRUCT-004-A", "Требования к функциональным характеристикам",
                    List.of("функциональные характеристики",
                            "требования к функциональным характеристикам"),
                    "ГОСТ 19.201-78 п.2.4.1"),
            new RequiredSection("STRUCT-004-B", "Требования к надёжности",
                    List.of("требования к надёжности", "требования к надежности",
                            "надёжность", "надежность"),
                    "ГОСТ 19.201-78 п.2.4.2"),
            new RequiredSection("STRUCT-004-C", "Условия эксплуатации",
                    List.of("условия эксплуатации"),
                    "ГОСТ 19.201-78 п.2.4.3"),
            new RequiredSection("STRUCT-004-D", "Требования к составу и параметрам технических средств",
                    List.of("состав и параметры технических средств",
                            "требования к составу и параметрам технических средств",
                            "требования к техническим средствам"),
                    "ГОСТ 19.201-78 п.2.4.4"),
            new RequiredSection("STRUCT-004-E", "Требования к информационной и программной совместимости",
                    List.of("информационная и программная совместимость",
                            "программная совместимость", "информационная совместимость",
                            "требования к информационной и программной совместимости"),
                    "ГОСТ 19.201-78 п.2.4.5")
    );

    @Override
    public String getCode() {
        return "STRUCT";
    }

    @Override
    public String getName() {
        return "Проверка структуры документа";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии STRUCT на документе");
        List<Violation> violations = new ArrayList<>();

        try {
            // Собираем тексты всех параграфов и заголовков
            List<String> headings = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText().trim();
                fullText.append(text.toLowerCase()).append("\n");

                // Считаем заголовком параграф со стилем Heading или полностью заглавный
                String style = paragraph.getStyle();
                if (style != null && style.toLowerCase().contains("heading")) {
                    headings.add(text.toLowerCase());
                } else if (!text.isEmpty() && text.equals(text.toUpperCase()) && text.length() > 3) {
                    headings.add(text.toLowerCase());
                }
            }

            String fullTextLower = fullText.toString();

            // ── Проверка обязательных разделов ────────────────────────────
            for (RequiredSection section : REQUIRED_SECTIONS) {
                boolean foundInHeadings = section.keywords.stream()
                        .anyMatch(kw -> headings.stream().anyMatch(h -> h.contains(kw)));
                boolean foundInText = section.keywords.stream()
                        .anyMatch(fullTextLower::contains);

                if (!foundInHeadings && !foundInText) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode(section.code)
                            .description("Отсутствует обязательный раздел: «" + section.name + "»")
                            .severity(ViolationSeverity.CRITICAL)
                            .pageNumber(0)
                            .lineNumber(0)
                            .suggestion("Добавьте раздел «" + section.name + "» в соответствии с " + section.gostRef)
                            .ruleReference(section.gostRef)
                            .build());
                } else if (!foundInHeadings && foundInText) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode(section.code)
                            .description("Раздел «" + section.name + "» присутствует в тексте, "
                                    + "но не оформлен как заголовок")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(0)
                            .lineNumber(findLineNumber(paragraphs, section.keywords))
                            .suggestion("Оформите «" + section.name + "» как заголовок (стиль Heading)")
                            .ruleReference(section.gostRef)
                            .build());
                }
            }

            // ── Проверка подразделов Требований к ПО ────────────────────
            boolean hasRequirementsSection = REQUIRED_SECTIONS.get(3).keywords.stream()
                    .anyMatch(fullTextLower::contains);

            if (hasRequirementsSection) {
                for (RequiredSection sub : REQUIREMENTS_SUBSECTIONS) {
                    boolean found = sub.keywords.stream()
                            .anyMatch(fullTextLower::contains);
                    if (!found) {
                        violations.add(Violation.builder()
                                .id(UUID.randomUUID())
                                .ruleCode(sub.code)
                                .description("Отсутствует подраздел: «" + sub.name + "»")
                                .severity(ViolationSeverity.WARNING)
                                .pageNumber(0)
                                .lineNumber(0)
                                .suggestion("Добавьте подраздел «" + sub.name + "» согласно " + sub.gostRef)
                                .ruleReference(sub.gostRef)
                                .build());
                    }
                }
            }

            log.info("StructureCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении StructureCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    // ── Вспомогательные методы ──────────────────────────────────────────

    /**
     * Найти номер параграфа, содержащего одно из ключевых слов.
     *
     * @param paragraphs список параграфов документа
     * @param keywords ключевые слова для поиска
     * @return номер параграфа (1-based) или 0 если не найден
     */
    private int findLineNumber(List<XWPFParagraph> paragraphs, List<String> keywords) {
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText().toLowerCase();
            for (String kw : keywords) {
                if (text.contains(kw)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /**
     * Создать нарушение об ошибке выполнения стратегии.
     *
     * @param e исключение, вызвавшее ошибку
     * @return {@link Violation} с типом INFO
     */
    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("STRUCT-ERR")
                .description("Ошибка при выполнении проверки структуры: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 19.201-78")
                .build();
    }

    /**
     * Внутренний record для описания требуемого раздела.
     */
    private record RequiredSection(
            String code,
            String name,
            List<String> keywords,
            String gostRef
    ) {}
}
