package ru.normacontrol.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemStatusController {

    private final DocumentJpaRepository documentJpaRepository;
    private final CheckResultJpaRepository checkResultJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = uptimeMs / 3600000;
        long minutes = (uptimeMs % 3600000) / 60000;
        String uptimeStr = hours + "ч " + minutes + "мин";
        long documents = documentJpaRepository.count();
        long checks = checkResultJpaRepository.count();
        long users = userJpaRepository.count();
        long checksToday = checkResultJpaRepository.countByCheckedAtAfter(LocalDate.now().atStartOfDay());
        double averageScore = checkResultJpaRepository.findAverageComplianceScore();

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "1.0.0",
                "author", "Идаят Али",
                "course", "Продвинутая Java, кафедра ИУ6",
                "uptime", uptimeStr,
                "oauth2Enabled", false,
                "components", Map.of(
                        "database", Map.of("status", "UP", "documents", documents, "checks", checks, "users", users)
                ),
                "statistics", Map.of(
                        "totalDocuments", documents,
                        "totalChecks", checks,
                        "averageScore", Math.round(averageScore),
                        "checksToday", checksToday
                )
        ));
    }

}
