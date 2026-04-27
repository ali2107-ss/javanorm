package ru.normacontrol.application.builder;

import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Итоговый отчёт о проверке документа.
 *
 * <p>Содержит полную информацию о документе, результатах проверки,
 * AI рекомендациях и дополнительных метаданных.</p>
 *
 * <p>Используется как {@link CheckReportBuilder#build() результат Builder},
 * а также передаётся в различные слои приложения (API, события, хранилище).</p>
 *
 * @param document исходный документ
 * @param checkResult результат проверки со списком нарушений
 * @param aiRecommendations Map с AI рекомендациями (код нарушения → текст)
 * @param comparisonResult результат сравнения с другим документом (если применимо)
 * @param reportGeneratedAt дата и время генерации отчёта
 * @param reportVersion версия формата отчёта
 * @param customMetadata дополнительные метаданные
 *
 * @see CheckReportBuilder
 */
public record CheckReportData(
        Document document,
        CheckResult checkResult,
        Map<String, String> aiRecommendations,
        Object comparisonResult,
        LocalDateTime reportGeneratedAt,
        String reportVersion,
        Map<String, Object> customMetadata
) {
    /**
     * Получить URL для доступа к документу (если применимо).
     *
     * <p>Может использоваться для генерации ссылок в отчётах.</p>
     *
     * @return URL документа или пустая строка
     */
    public String getDocumentUrl() {
        return "/api/documents/" + document.getId();
    }

    /**
     * Получить статус прохождения проверки в человекочитаемом формате.
     *
     * @return "PASSED" или "FAILED"
     */
    public String getStatus() {
        return checkResult.isPassed() ? "PASSED" : "FAILED";
    }

    /**
     * Получить процент прохождения (для UI).
     *
     * @return значение от 0 до 100
     */
    public int getCompliancePercentage() {
        if (checkResult.getTotalViolations() == 0) {
            return 100;
        }
        long criticalCount = checkResult.getViolations().stream()
                .filter(v -> "CRITICAL".equals(v.getSeverity().toString()))
                .count();
        return Math.max(0, 100 - (int)(criticalCount * 10));
    }

    /**
     * Получить краткую сводку отчёта для логов.
     *
     * @return строка вида "Document: name | Status: PASSED | Violations: 5"
     */
    @Override
    public String toString() {
        return String.format(
                "CheckReport{document=%s, status=%s, violations=%d, generated=%s}",
                document.getFileName(),
                getStatus(),
                checkResult.getTotalViolations(),
                reportGeneratedAt
        );
    }
}
