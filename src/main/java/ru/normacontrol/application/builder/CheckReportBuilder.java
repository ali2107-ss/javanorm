package ru.normacontrol.application.builder;

import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Строитель для конструирования итоговых отчётов о проверке документа.
 *
 * <p>Реализует паттерн <b>Builder</b>: позволяет поэтапно собирать
 * сложный объект отчёта из различных источников данных
 * (результат проверки, AI рекомендации, метаинформация и т.д.)</p>
 *
 * <p><b>Использование:</b></p>
 * <pre>
 * CheckReportBuilder report = new CheckReportBuilder()
 *     .withDocument(doc)
 *     .withCheckResult(checkResult)
 *     .withAiRecommendations(aiRecs)
 *     .withComparisonResult(comparisonData)
 *     .build();
 * </pre>
 *
 * <p><b>Преимущества:</b></p>
 * <ul>
 *   <li>Читаемость: порядок вызовов методов ясен из кода</li>
 *   <li>Гибкость: можно добавлять/пропускать нужные данные</li>
 *   <li>Инкапсуляция: логика сборки отделена от использования</li>
 *   <li>Сложность: удобно собирать объекты с множеством параметров</li>
 * </ul>
 *
 * @see CheckReportData
 * @see CheckResultResponse
 */
public class CheckReportBuilder {

    private Document document;
    private CheckResult checkResult;
    private Map<String, String> aiRecommendations;
    private Object comparisonResult;
    private LocalDateTime reportGeneratedAt;
    private String reportVersion;
    private Map<String, Object> customMetadata;

    /**
     * Конструктор — инициализирует пустойBuilder.
     */
    public CheckReportBuilder() {
        this.aiRecommendations = new HashMap<>();
        this.customMetadata = new HashMap<>();
        this.reportGeneratedAt = LocalDateTime.now();
        this.reportVersion = "1.0";
    }

    /**
     * Установить документ для отчёта.
     *
     * @param document исходный документ, прошедший проверку
     * @return {@code this} для chaining
     *
     * @throws IllegalArgumentException если document равен null
     */
    public CheckReportBuilder withDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Документ не может быть null");
        }
        this.document = document;
        return this;
    }

    /**
     * Установить результат проверки (список нарушений, оценка и т.д.).
     *
     * @param checkResult результат выполнения проверки ГОСТ 19.201-78
     * @return {@code this} для chaining
     *
     * @throws IllegalArgumentException если checkResult равен null
     */
    public CheckReportBuilder withCheckResult(CheckResult checkResult) {
        if (checkResult == null) {
            throw new IllegalArgumentException("Результат проверки не может быть null");
        }
        this.checkResult = checkResult;
        return this;
    }

    /**
     * Добавить AI рекомендации для нарушений.
     *
     * <p>Рекомендации — это Map вида:</p>
     * <pre>
     * {
     *   "STRUCT-001": "Добавьте раздел Введение в начало документа",
     *   "FMT-002": "Установите размер шрифта 14 пт для основного текста",
     *   ...
     * }
     * </pre>
     *
     * @param aiRecommendations Map с рекомендациями (код нарушения → рекомендация)
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withAiRecommendations(Map<String, String> aiRecommendations) {
        if (aiRecommendations != null) {
            this.aiRecommendations.putAll(aiRecommendations);
        }
        return this;
    }

    /**
     * Добавить одну AI рекомендацию.
     *
     * @param violationCode код нарушения (например, "STRUCT-001")
     * @param recommendation текст рекомендации
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withAiRecommendation(String violationCode, String recommendation) {
        if (violationCode != null && recommendation != null) {
            this.aiRecommendations.put(violationCode, recommendation);
        }
        return this;
    }

    /**
     * Установить результат сравнения с другим документом (если применимо).
     *
     * <p>Используется для функции сравнения документов.</p>
     *
     * @param comparisonResult результат сравнения (структура не определена)
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withComparisonResult(Object comparisonResult) {
        this.comparisonResult = comparisonResult;
        return this;
    }

    /**
     * Установить дату/время генерации отчёта.
     *
     * <p>По умолчанию используется текущее время.</p>
     *
     * @param generatedAt дата и время
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withReportGeneratedAt(LocalDateTime generatedAt) {
        if (generatedAt != null) {
            this.reportGeneratedAt = generatedAt;
        }
        return this;
    }

    /**
     * Установить версию формата отчёта.
     *
     * <p>По умолчанию "1.0".</p>
     *
     * @param version версия (например, "1.0", "1.1")
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withReportVersion(String version) {
        if (version != null && !version.isBlank()) {
            this.reportVersion = version;
        }
        return this;
    }

    /**
     * Добавить произвольные метаданные в отчёт.
     *
     * @param metadata Key-Value пары с дополнительной информацией
     * @return {@code this} для chaining
     */
    public CheckReportBuilder withCustomMetadata(Map<String, Object> metadata) {
        if (metadata != null) {
            this.customMetadata.putAll(metadata);
        }
        return this;
    }

    /**
     * Добавить одно произвольное свойство метаданных.
     *
     * @param key ключ
     * @param value значение
     * @return {@code this} для chaining
     */
    public CheckReportBuilder addMetadata(String key, Object value) {
        if (key != null) {
            this.customMetadata.put(key, value);
        }
        return this;
    }

    /**
     * Построить итоговый отчёт.
     *
     * <p>Проверяет, что установлены обязательные поля
     * ({@code document} и {@code checkResult}), а затем
     * создаёт объект {@link CheckReportData} со всеми данными.</p>
     *
     * @return построенный отчёт {@link CheckReportData}
     *
     * @throws IllegalStateException если не установлены обязательные поля
     */
    public CheckReportData build() {
        if (document == null) {
            throw new IllegalStateException("Документ обязателен для отчёта. Используйте withDocument()");
        }
        if (checkResult == null) {
            throw new IllegalStateException("Результат проверки обязателен. Используйте withCheckResult()");
        }

        return new CheckReportData(
                document,
                checkResult,
                aiRecommendations,
                comparisonResult,
                reportGeneratedAt,
                reportVersion,
                customMetadata
        );
    }
}
