package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO — точка на графике динамики баллов пользователя.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTrendDto {

    /** Дата (день) агрегации. */
    private LocalDate date;

    /** Средний балл за этот день. */
    private double averageScore;

    /** Количество проверок за этот день. */
    private long checksCount;
}
