package ru.normacontrol.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

/**
 * Admin endpoints for audit logs and aggregate statistics.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Администрирование", description = "Audit log и статистика для ADMIN")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final DocumentJpaRepository documentJpaRepository;
    private final CheckResultJpaRepository checkResultJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ViolationJpaRepository violationJpaRepository;

    /**
     * Return paged audit log entries.
     *
     * @param userId optional user filter
     * @param action optional action filter
     * @param dateFrom optional lower bound
     * @param dateTo optional upper bound
     * @param page zero-based page
     * @param size page size
     * @return paged audit DTOs
     */
    @GetMapping("/audit")
    public Page<AuditLogDto> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditLogRepository.findByFilters(userId, action, dateFrom, dateTo, PageRequest.of(page, size))
                .map(AuditLogDto::from);
    }

    /**
     * Export audit logs as CSV.
     *
     * @param dateFrom optional lower bound
     * @param dateTo optional upper bound
     * @return CSV payload
     */
    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
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
    }

    /**
     * Return high-level admin statistics.
     *
     * @return admin statistics DTO
     */
    @GetMapping("/stats")
    public AdminStatsDto getStats() {
        List<ViolationStatDto> topViolations = violationJpaRepository.findTopViolations().stream()
                .map(row -> new ViolationStatDto(row.getRuleCode(), row.getCount(), row.getDescription()))
                .toList();

        return new AdminStatsDto(
                documentJpaRepository.countByDeletedFalse(),
                checkResultJpaRepository.count(),
                checkResultJpaRepository.findAverageComplianceScore(),
                checkResultJpaRepository.countByCheckedAtAfter(LocalDate.now().atStartOfDay()),
                userJpaRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(7)),
                topViolations
        );
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
