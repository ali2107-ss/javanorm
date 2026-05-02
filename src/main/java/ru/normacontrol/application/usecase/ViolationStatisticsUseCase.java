package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.response.ScoreTrendDto;
import ru.normacontrol.application.dto.response.SectionComplianceDto;
import ru.normacontrol.application.dto.response.ViolationStatDto;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.ViolationJpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use case для статистики нарушений и динамики баллов.
 *
 * <p>Предоставляет три вида аналитики:
 * <ol>
 *   <li>{@link #getTopViolations} — топ-N частых нарушений по всем документам</li>
 *   <li>{@link #getScoreTrend}    — динамика среднего балла за N дней</li>
 *   <li>{@link #getComplianceBySection} — разбивка нарушений по разделам ГОСТ</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationStatisticsUseCase {

    /**
     * Описания разделов ГОСТ и их код-префиксы правил.
     * Порядок определяет приоритет при отображении.
     */
    private static final List<Map.Entry<String, String>> SECTIONS = List.of(
            Map.entry("STRUCT", "Структура документа"),
            Map.entry("FMT",    "Форматирование"),
            Map.entry("TBL",    "Таблицы"),
            Map.entry("FIG",    "Рисунки"),
            Map.entry("LANG",   "Язык и стиль"),
            Map.entry("REF",    "Ссылки и литература")
    );

    private final ViolationJpaRepository    violationJpaRepository;
    private final CheckResultJpaRepository  checkResultJpaRepository;

    // ── API ────────────────────────────────────────────────────────────────────

    /**
     * Топ-N наиболее частых нарушений по всем документам.
     *
     * @param limit максимальное количество записей (обычно 10)
     * @return список DTO, отсортированный по убыванию count
     */
    @Transactional(readOnly = true)
    public List<ViolationStatDto> getTopViolations(int limit) {
        log.debug("Запрос топ-{} нарушений", limit);

        List<ViolationJpaRepository.TopViolationProjection> raw =
                violationJpaRepository.findTopViolations();

        long total = raw.stream().mapToLong(ViolationJpaRepository.TopViolationProjection::getCount).sum();
        if (total == 0) total = 1;

        List<ViolationStatDto> result = new ArrayList<>();
        for (ViolationJpaRepository.TopViolationProjection row : raw) {
            double pct = total > 0 ? (double) row.getCount() / total * 100.0 : 0;
            result.add(ViolationStatDto.builder()
                    .ruleCode(row.getRuleCode())
                    .description(row.getDescription())
                    .count(row.getCount())
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .avgSeverity(inferSeverityFromCode(row.getRuleCode()))
                    .build());
        }

        return result.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Динамика среднего балла за последние N дней.
     *
     * <p>Если за день не было проверок, точка в тренде пропускается.</p>
     *
     * @param days количество дней от текущей даты назад
     * @return список точек графика, отсортированных по дате
     */
    @Transactional(readOnly = true)
    public List<ScoreTrendDto> getScoreTrend(int days) {
        log.debug("Запрос динамики баллов за {} дней", days);

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity> results =
                checkResultJpaRepository.findAll().stream()
                        .filter(r -> r.getCheckedAt() != null && r.getCheckedAt().isAfter(since))
                        .collect(Collectors.toList());

        // Группируем по дням
        Map<LocalDate, List<ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity>> byDay =
                results.stream()
                        .collect(Collectors.groupingBy(r -> r.getCheckedAt().toLocalDate()));

        return byDay.entrySet().stream()
                .map(e -> {
                    List<ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity> dayResults = e.getValue();
                    double avg = dayResults.stream()
                            .filter(r -> r.getComplianceScore() != null)
                            .mapToInt(ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity::getComplianceScore)
                            .average()
                            .orElse(0.0);
                    return ScoreTrendDto.builder()
                            .date(e.getKey())
                            .averageScore(Math.round(avg * 10.0) / 10.0)
                            .checksCount(dayResults.size())
                            .build();
                })
                .sorted(Comparator.comparing(ScoreTrendDto::getDate))
                .collect(Collectors.toList());
    }

    /**
     * Статистика нарушений в разрезе разделов ГОСТ.
     *
     * <p>Группирует нарушения по коду-префиксу правила (STRUCT, FMT, TBL, FIG, LANG, REF).
     * Для каждого раздела вычисляет долю нарушений и процент соответствия.</p>
     *
     * @return список разделов с показателями соответствия
     */
    @Transactional(readOnly = true)
    public List<SectionComplianceDto> getComplianceBySection() {
        log.debug("Запрос статистики по разделам ГОСТ");

        List<ViolationJpaRepository.TopViolationProjection> allViolations =
                violationJpaRepository.findTopViolations();

        // Общее количество нарушений
        long grandTotal = allViolations.stream()
                .mapToLong(ViolationJpaRepository.TopViolationProjection::getCount)
                .sum();

        // Группируем по префиксу кода правила
        Map<String, Long> countByPrefix = allViolations.stream()
                .collect(Collectors.groupingBy(
                        v -> extractPrefix(v.getRuleCode()),
                        Collectors.summingLong(ViolationJpaRepository.TopViolationProjection::getCount)
                ));

        long effectiveTotal = Math.max(grandTotal, 1);

        return SECTIONS.stream()
                .map(section -> {
                    String prefix = section.getKey();
                    String name   = section.getValue();
                    long   count  = countByPrefix.getOrDefault(prefix, 0L);
                    double share  = (double) count / effectiveTotal * 100.0;
                    double compliance = 100.0 - share;

                    return SectionComplianceDto.builder()
                            .sectionName(name)
                            .rulePrefix(prefix)
                            .violationCount(count)
                            .violationShare(Math.round(share * 10.0) / 10.0)
                            .complianceRate(Math.round(compliance * 10.0) / 10.0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Извлечь префикс из кода правила (например, «FMT» из «FMT-002»).
     */
    private String extractPrefix(String ruleCode) {
        if (ruleCode == null) return "OTHER";
        int dash = ruleCode.indexOf('-');
        return dash > 0 ? ruleCode.substring(0, dash) : ruleCode;
    }

    /**
     * Определить средний уровень нарушения по префиксу кода.
     *
     * <p>Эвристика: STRUCT → CRITICAL, FMT → WARNING, прочие → INFO.</p>
     */
    private String inferSeverityFromCode(String ruleCode) {
        if (ruleCode == null) return "INFO";
        String prefix = extractPrefix(ruleCode);
        return switch (prefix) {
            case "STRUCT" -> "CRITICAL";
            case "FMT"    -> "WARNING";
            case "TBL"    -> "WARNING";
            default       -> "INFO";
        };
    }
}
