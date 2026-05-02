package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO — соответствие разделов ГОСТ требованиям.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionComplianceDto {

    /** Название раздела ГОСТ (например, «Форматирование»). */
    private String sectionName;

    /** Код-префикс правил раздела (FMT, STRUCT и т.д.). */
    private String rulePrefix;

    /** Общее количество нарушений по разделу. */
    private long violationCount;

    /** Доля нарушений от всех нарушений, 0..100. */
    private double violationShare;

    /** Процент соответствия (100 - violationShare). */
    private double complianceRate;
}
