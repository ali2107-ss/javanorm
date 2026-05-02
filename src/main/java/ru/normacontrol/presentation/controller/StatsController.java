package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.normacontrol.application.dto.response.ScoreTrendDto;
import ru.normacontrol.application.dto.response.SectionComplianceDto;
import ru.normacontrol.application.dto.response.ViolationStatDto;
import ru.normacontrol.application.usecase.ViolationStatisticsUseCase;

import java.util.List;

/**
 * REST-контроллер аналитики и статистики нарушений.
 *
 * <p>Эндпоинты для страницы «Статистика» (stats.html):
 * <ul>
 *   <li>GET /api/v1/stats/top-violations — топ частых нарушений</li>
 *   <li>GET /api/v1/stats/score-trend    — динамика баллов</li>
 *   <li>GET /api/v1/stats/by-section     — разбивка по разделам ГОСТ</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Статистика", description = "Аналитика нарушений и динамика баллов")
public class StatsController {

    private final ViolationStatisticsUseCase violationStatisticsUseCase;

    /**
     * Топ-N наиболее частых нарушений по всем документам.
     *
     * @param limit максимальное количество результатов (по умолчанию 10)
     * @return список нарушений с частотой и долей в процентах
     */
    @GetMapping("/top-violations")
    @Operation(
        summary = "Топ нарушений",
        description = "Возвращает N наиболее часто встречаемых нарушений ГОСТ по всем документам"
    )
    public ResponseEntity<List<ViolationStatDto>> getTopViolations(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(violationStatisticsUseCase.getTopViolations(limit));
    }

    /**
     * Динамика среднего балла за последние N дней.
     *
     * @param days количество дней (по умолчанию 30)
     * @return список точек тренда с датой и средним баллом
     */
    @GetMapping("/score-trend")
    @Operation(
        summary = "Динамика баллов",
        description = "График среднего балла соответствия ГОСТ за последние N дней"
    )
    public ResponseEntity<List<ScoreTrendDto>> getScoreTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(violationStatisticsUseCase.getScoreTrend(days));
    }

    /**
     * Разбивка нарушений по разделам ГОСТ 19.201-78.
     *
     * @return список разделов с долей нарушений и процентом соответствия
     */
    @GetMapping("/by-section")
    @Operation(
        summary = "Нарушения по разделам ГОСТ",
        description = "Разбивка нарушений по разделам ГОСТ: структура, форматирование, таблицы и т.д."
    )
    public ResponseEntity<List<SectionComplianceDto>> getBySection() {
        return ResponseEntity.ok(violationStatisticsUseCase.getComplianceBySection());
    }
}
