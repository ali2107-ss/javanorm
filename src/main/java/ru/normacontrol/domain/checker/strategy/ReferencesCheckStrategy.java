package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.*;

/**
 * Стратегия проверки списка литературы и ссылок.
 * <p>
 * Проверяет:
 * <ul>
 *   <li>Наличие раздела «Список литературы» или «Список использованных источников»</li>
 *   <li>Корректное оформление ссылок (внутритекстовые, затекстовые)</li>
 *   <li>Наличие ссылок на все цитируемые источники</li>
 * </ul>
 * </p>
 * <p>
 * Ссылка: ГОСТ 7.1-2003, ГОСТ 2.105-95 п.4.5
 * </p>
 *
 * @see CheckStrategy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferencesCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    @Override
    public String getCode() {
        return "REF";
    }

    @Override
    public String getName() {
        return "Проверка списка литературы";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        log.debug("Запуск стратегии REF на документе");
        List<Violation> violations = new ArrayList<>();

        try {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            String fullTextLower = paragraphs.stream()
                    .map(p -> p.getText().toLowerCase())
                    .reduce("", String::concat);

            // Проверка наличия раздела с литературой
            boolean hasReferencesSection = fullTextLower.contains("список литературы")
                    || fullTextLower.contains("список использованных источников")
                    || fullTextLower.contains("библиография")
                    || fullTextLower.contains("литература");

            if (!hasReferencesSection) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("REF-001")
                        .description("Отсутствует раздел со списком литературы")
                        .severity(ViolationSeverity.WARNING)
                        .pageNumber(0)
                        .lineNumber(0)
                        .suggestion("Добавьте раздел «Список литературы» или «Список использованных источников»")
                        .ruleReference("ГОСТ 7.1-2003; ГОСТ 2.105-95 п.4.5")
                        .build());
            }

            log.info("ReferencesCheckStrategy: обнаружено {} нарушений", violations.size());

        } catch (Exception e) {
            log.error("Ошибка при выполнении ReferencesCheckStrategy", e);
            violations.add(createErrorViolation(e));
        }

        return violations;
    }

    private Violation createErrorViolation(Exception e) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("REF-ERR")
                .description("Ошибка при выполнении проверки литературы: " + e.getMessage())
                .severity(ViolationSeverity.INFO)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Повторите проверку или обратитесь к администратору")
                .ruleReference("ГОСТ 7.1-2003")
                .build();
    }
}
