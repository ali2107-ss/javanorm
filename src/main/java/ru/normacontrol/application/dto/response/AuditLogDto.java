package ru.normacontrol.application.dto.response;

import ru.normacontrol.infrastructure.audit.AuditLog;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO returned by the admin audit API.
 *
 * @param id row identifier
 * @param userId user identifier
 * @param userEmail user email
 * @param action action code
 * @param resourceType resource type
 * @param resourceId resource identifier
 * @param ipAddress client IP address
 * @param userAgent browser or client agent
 * @param details structured JSON details
 * @param success success flag
 * @param errorMessage optional error message
 * @param timestamp row timestamp
 */
public record AuditLogDto(
        UUID id,
        UUID userId,
        String userEmail,
        String action,
        String resourceType,
        UUID resourceId,
        String ipAddress,
        String userAgent,
        Map<String, Object> details,
        boolean success,
        String errorMessage,
        LocalDateTime timestamp
) {

    /**
     * Convert entity to DTO.
     *
     * @param auditLog source entity
     * @return DTO view
     */
    public static AuditLogDto from(AuditLog auditLog) {
        return new AuditLogDto(
                auditLog.getId(),
                auditLog.getUserId(),
                auditLog.getUserEmail(),
                auditLog.getAction(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getDetails(),
                auditLog.isSuccess(),
                auditLog.getErrorMessage(),
                auditLog.getTimestamp()
        );
    }
}
