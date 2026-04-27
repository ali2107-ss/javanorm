package ru.normacontrol.domain.entity;

import lombok.*;
import ru.normacontrol.domain.enums.ViolationSeverity;

import java.util.UUID;

/**
 * Доменная сущность нарушения ГОСТ 19.201-78.
 *
 * <p>Каждое нарушение фиксирует код правила, описание проблемы,
 * точное местоположение (страница/строка), уровень критичности,
 * рекомендацию по исправлению и ссылку на пункт ГОСТ.</p>
 *
 * <h3>Правила нарушения:</h3>
 * <ul>
 *   <li><b>Code</b> → уникальный код (например, STRUCT-001, FMT-002)</li>
 *   <li><b>Severity</b> → уровень важности (CRITICAL, WARNING, INFO)</li>
 *   <li><b>Location</b> → точное место в документе (страница, строка)</li>
 *   <li><b>Reference</b> → ссылка на пункт ГОСТ для подтверждения</li>
 * </ul>
 *
 * <h3>Коды нарушений по стратегиям проверки:</h3>
 * <ul>
 *   <li><b>STRUCT-*</b> — стратегия {@code StructureCheckStrategy}</li>
 *   <li><b>FMT-*</b> — стратегия {@code FormattingCheckStrategy}</li>
 *   <li><b>TBL-*</b> — стратегия {@code TablesCheckStrategy}</li>
 *   <li><b>FIG-*</b> — стратегия {@code FiguresCheckStrategy}</li>
 *   <li><b>LANG-*</b> — стратегия {@code LanguageCheckStrategy}</li>
 *   <li><b>REF-*</b> — стратегия {@code ReferencesCheckStrategy}</li>
 * </ul>
 *
 * <h3>Инварианты сущности:</h3>
 * <ul>
 *   <li>{@code id} уникален и неизменяем</li>
 *   <li>{@code severity} определяет, заставляет ли нарушение документ провалить проверку</li>
 *   <li>{@code pageNumber = 0} значит нарушение не привязано к конкретной странице</li>
 *   <li>{@code lineNumber = 0} значит нарушение в целом документе</li>
 *   <li>{@code ruleReference} ссылается на конкретный пункт ГОСТ (например, п.2.1, п.2.4.5)</li>
 * </ul>
 *
 * @see CheckResult
 * @see ViolationSeverity
 * @see ru.normacontrol.domain.checker.strategy.CheckStrategy
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    /** Уникальный идентификатор нарушения (UUID v4). */
    private UUID id;

    /**
     * Код правила проверки (например, {@code STRUCT-001}, {@code FMT-002}).
     *
     * <p>Формат кода: {@code STRATEGY-NUMBER}
     * <ul>
     *   <li>{@code STRATEGY} — 4 символа (STRUCT, FMT, TBL, FIG, LANG, REF)</li>
     *   <li>{@code NUMBER} — порядковый номер в стратегии (001, 002, ...)</li>
     * </ul>
     * </p>
     */
    private String ruleCode;

    /**
     * Человекочитаемое описание нарушения.
     *
     * <p>Примеры:
     * <ul>
     *   <li>"Отсутствует обязательный раздел: «Введение»"</li>
     *   <li>"Обнаружен кегль 12 пт вместо 14 пт"</li>
     *   <li>"Таблица не имеет ссылки в тексте"</li>
     * </ul>
     * </p>
     */
    private String description;

    /**
     * Уровень критичности нарушения.
     *
     * <p>Влияет на результат проверки ({@link CheckResult#evaluate()}):</p>
     * <ul>
     *   <li>{@link ViolationSeverity#CRITICAL} — документ ПРОВАЛИТ проверку</li>
     *   <li>{@link ViolationSeverity#WARNING} — может быть проигнорировано</li>
     *   <li>{@link ViolationSeverity#INFO} — рекомендация или примечание</li>
     * </ul>
     */
    private ViolationSeverity severity;

    /**
     * Номер страницы, на которой обнаружено нарушение.
     *
     * <p>Примечания:</p>
     * <ul>
     *   <li>{@code 0} = нарушение относится ко всему документу (не к конкретной странице)</li>
     *   <li>{@code 1..N} = номер конкретной страницы (1-based)</li>
     * </ul>
     */
    private int pageNumber;

    /**
     * Номер строки/параграфа, в котором обнаружено нарушение.
     *
     * <p>Примечания:</p>
     * <ul>
     *   <li>{@code 0} = не применимо или нарушение в целом документе</li>
     *   <li>{@code 1..N} = номер параграфа в документе (1-based)</li>
     * </ul>
     */
    private int lineNumber;

    /**
     * Рекомендация по исправлению нарушения (от стратегии).
     *
     * <p>Примеры:
     * <ul>
     *   <li>"Добавьте раздел «Введение» в соответствии с ГОСТ 19.201-78 п.2.1"</li>
     *   <li>"Установите размер шрифта 14 пт для основного текста"</li>
     *   <li>"Нумеруйте таблицы последовательно: 1, 2, 3…"</li>
     * </ul>
     * </p>
     */
    private String suggestion;

    /**
     * Умная рекомендация от AI (опционально).
     *
     * <p>Заполняется асинхронно после выполнения проверки
     * сервисом {@link ru.normacontrol.infrastructure.ai.AiRecommendationService}.
     * Содержит контекстуальные и персонализированные советы.</p>
     */
    private String aiSuggestion;

    /**
     * Ссылка на пункт ГОСТ 19.201-78 где применимо.
     *
     * <p>Примеры:
     * <ul>
     *   <li>"ГОСТ 19.201-78 п.2.1" — структура</li>
     *   <li>"ГОСТ 19.201-78 п.1.3; ГОСТ 2.105-95 п.4.1" — форматирование</li>
     *   <li>"ГОСТ 2.105-95 п.4.4" — таблицы</li>
     * </ul>
     * </p>
     */
    private String ruleReference;

    /**
     * Получить краткую информацию о нарушении для логирования.
     *
     * @return строка вида "[STRUCT-001] CRITICAL: Отсутствует раздел Введение (п.2.1)"
     */
    @Override
    public String toString() {
        return String.format("[%s] %s: %s (%s)",
                ruleCode, severity, description, ruleReference);
    }
}
