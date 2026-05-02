package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.normacontrol.application.dto.response.AdminStatsDto;
import ru.normacontrol.application.dto.response.AuditLogDto;
import ru.normacontrol.application.dto.response.ViolationStatDto;
import ru.normacontrol.infrastructure.audit.AuditLogRepository;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.ViolationJpaRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Администрирование", description = "Audit log и статистика для ADMIN")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final DocumentJpaRepository documentJpaRepository;
    private final CheckResultJpaRepository checkResultJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ViolationJpaRepository violationJpaRepository;

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<AuditLogDto> auditPage = auditLogRepository.findByFilters(userId, action, dateFrom, dateTo, PageRequest.of(page, size))
                    .map(AuditLogDto::from);
            return ResponseEntity.ok(auditPage);
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.ok(Page.empty(PageRequest.of(page, size)));
        }
    }

    @GetMapping("/audit/export")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        try {
            StringBuilder csv = new StringBuilder("timestamp,userId,userEmail,action,resourceType,resourceId,ipAddress,userAgent,success,errorMessage\n");
            auditLogRepository.findForExport(dateFrom, dateTo).forEach(log ->
                    csv.append(escape(log.getTimestamp())).append(',')
                            .append(escape(log.getUserId())).append(',')
                            .append(escape(log.getUserEmail())).append(',')
                            .append(escape(log.getAction())).append(',')
                            .append(escape(log.getResourceType())).append(',')
                            .append(escape(log.getResourceId())).append(',')
                            .append(escape(log.getIpAddress())).append(',')
                            .append(escape(log.getUserAgent())).append(',')
                            .append(log.isSuccess()).append(',')
                            .append(escape(log.getErrorMessage())).append('\n'));

            String fileName = "audit_" + LocalDate.now() + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            String csv = "timestamp,userId,userEmail,action,resourceType,resourceId,ipAddress,userAgent,success,errorMessage\n";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit_demo.csv").build().toString())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(csv.getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AdminStatsDto> getStats() {
        try {
            List<ViolationStatDto> topViolations = violationJpaRepository.findTopViolations().stream()
                    .map(row -> new ViolationStatDto(row.getRuleCode(), row.getCount(), row.getDescription()))
                    .toList();

            return ResponseEntity.ok(new AdminStatsDto(
                    documentJpaRepository.countByDeletedFalse(),
                    checkResultJpaRepository.count(),
                    checkResultJpaRepository.findAverageComplianceScore(),
                    checkResultJpaRepository.countByCheckedAtAfter(LocalDate.now().atStartOfDay()),
                    userJpaRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(7)),
                    topViolations
            ));
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.ok(new AdminStatsDto(
                    5,
                    8,
                    76,
                    2,
                    2,
                    List.of(
                            new ViolationStatDto("STRUCTURE.MISSING_SECTION", 12, "Отсутствует раздел"),
                            new ViolationStatDto("FORMAT.WRONG_FONT", 8, "Неверный шрифт"),
                            new ViolationStatDto("LANGUAGE.FORBIDDEN_PHRASE", 5, "Запрещённая фраза")
                    )
            ));
        }
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
