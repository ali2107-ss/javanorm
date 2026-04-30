package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Strategy for table validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TablesCheckStrategy implements CheckStrategy {

    private final CheckStrategySettingsService settingsService;

    @Override
    public String getCode() {
        return "TBL";
    }

    @Override
    public boolean isEnabled() {
        return settingsService.isEnabled(getCode());
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public List<Violation> execute(XWPFDocument document) {
        List<Violation> violations = new ArrayList<>();
        List<XWPFTable> tables = document.getTables();
        if (tables.isEmpty()) {
            return violations;
        }

        boolean hasCaption = document.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .filter(text -> text != null)
                .map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(text -> text.startsWith("таблица "));

        if (!hasCaption) {
            violations.add(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("TBL-001")
                    .description("У таблицы отсутствует подпись")
                    .severity(ViolationSeverity.WARNING)
                    .pageNumber(0)
                    .lineNumber(0)
                    .suggestion("Добавьте подпись вида \"Таблица 1 - Название\"")
                    .ruleReference("ГОСТ 2.105-95 п.4.4.1")
                    .build());
        }

        log.info("TablesCheckStrategy found {} violations", violations.size());
        return violations;
    }
}
