package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.infrastructure.persistence.entity.CheckStrategySettingJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.CheckStrategySettingJpaRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rule-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Наборы правил", description = "Управление включением и отключением стратегий нормоконтроля")
public class RuleSettingsController {

    private final CheckStrategySettingJpaRepository repository;

    @GetMapping
    @Operation(summary = "Получить текущий набор правил")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<List<RuleSettingDto>> list() {
        return ResponseEntity.ok(repository.findAll().stream()
                .sorted(Comparator.comparing(CheckStrategySettingJpaEntity::getStrategyCode))
                .map(RuleSettingDto::from)
                .toList());
    }

    @GetMapping("/export")
    @Operation(summary = "Экспортировать набор правил в JSON")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<List<RuleSettingDto>> exportRules() {
        return list();
    }

    @PostMapping("/import")
    @Operation(summary = "Импортировать набор правил из JSON")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<List<RuleSettingDto>> importRules(@RequestBody List<RuleSettingDto> settings) {
        repository.saveAll(settings.stream()
                .map(dto -> CheckStrategySettingJpaEntity.builder()
                        .strategyCode(dto.strategyCode())
                        .enabled(dto.enabled())
                        .build())
                .toList());
        return list();
    }

    @PatchMapping
    @Operation(summary = "Включить или отключить отдельные правила")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<List<RuleSettingDto>> update(@RequestBody List<RuleSettingDto> settings) {
        return importRules(settings);
    }

    public record RuleSettingDto(String strategyCode, boolean enabled) {
        static RuleSettingDto from(CheckStrategySettingJpaEntity entity) {
            return new RuleSettingDto(entity.getStrategyCode(), entity.isEnabled());
        }
    }
}
