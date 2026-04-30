package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Strategy for language validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageCheckStrategy implements CheckStrategy {

    private static final List<String> FORBIDDEN_PHRASES = List.of("и т.д.", "и т.п.", "и пр.", "и др.");
    private static final Pattern PAST_TENSE_PATTERN = Pattern.compile(
            "\\b(был|была|было|использовал|использовала|разработал|разработала|выполнял|выполняла)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final CheckStrategySettingsService settingsService;

    @Override
    public String getCode() {
        return "LANG";
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
        List<Violation> violations = new ArrayList<>();

        for (int i = 0; i < document.getParagraphs().size(); i++) {
            String text = document.getParagraphs().get(i).getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase();

            for (String phrase : FORBIDDEN_PHRASES) {
                if (lower.contains(phrase)) {
                    violations.add(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("LANG-001")
                            .description("Запрещенная фраза: " + phrase)
                            .severity(ViolationSeverity.CRITICAL)
                            .pageNumber(0)
                            .lineNumber(i + 1)
                            .suggestion("Уберите сокращение")
                            .ruleReference("ГОСТ 2.105-95 п.4.2.7")
                            .build());
                }
            }

            if (PAST_TENSE_PATTERN.matcher(lower).find()) {
                violations.add(Violation.builder()
                        .id(UUID.randomUUID())
                        .ruleCode("LANG-002")
                        .description("Обнаружено прошедшее время")
                        .severity(ViolationSeverity.WARNING)
                        .pageNumber(0)
                        .lineNumber(i + 1)
                        .suggestion("Используйте настоящее время")
                        .ruleReference("ГОСТ 19.201-78 п.1.4")
                        .build());
            }
        }

        log.info("LanguageCheckStrategy found {} violations", violations.size());
        return violations;
    }
}
