package ru.normacontrol.domain.entity;

import lombok.*;
import ru.normacontrol.domain.enums.ViolationSeverity;
import java.util.UUID;

/**
 * Доменная сущность нарушения ГОСТ 19.201-78.
 * <p>
 * Каждое нарушение фиксирует код правила, описание проблемы,
 * точное местоположение (страница/строка), уровень критичности,
 * рекомендацию по исправлению и ссылку на пункт ГОСТ.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    /** Уникальный идентификатор нарушения. */
    private UUID id;

    /** Код правила проверки (например, STRUCT-001, FMT-002). */
    private String ruleCode;

    /** Человекочитаемое описание нарушения. */
    private String description;

    /** Уровень критичности: CRITICAL, WARNING, INFO. */
    private ViolationSeverity severity;

    /** Номер страницы, на которой обнаружено нарушение (0 = весь документ). */
    private int pageNumber;

    /** Номер строки/параграфа, в котором обнаружено нарушение (0 = не применимо). */
    private int lineNumber;

    /** Рекомендация по исправлению. */
    private String suggestion;

    /** Умная рекомендация от AI. */
    private String aiSuggestion;

    /** Ссылка на пункт ГОСТ 19.201-78 (например, «п.2.1», «п.2.4.1»). */
    private String ruleReference;
}
