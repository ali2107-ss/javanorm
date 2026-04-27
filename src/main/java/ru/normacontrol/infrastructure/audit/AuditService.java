package ru.normacontrol.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.normacontrol.domain.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes audit log entries asynchronously in independent transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Persist an audit record.
     *
     * @param userId acting user identifier
     * @param action action code
     * @param resourceType resource type
     * @param resourceId resource identifier
     * @param success success flag
     * @param details structured details
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId,
                    String action,
                    String resourceType,
                    UUID resourceId,
                    boolean success,
                    Map<String, Object> details) {
        logInternal(userId, action, resourceType, resourceId, success, details, null);
    }

    /**
     * Persist an audit record with explicit error message.
     *
     * @param userId acting user identifier
     * @param action action code
     * @param resourceType resource type
     * @param resourceId resource identifier
     * @param success success flag
     * @param details structured details
     * @param errorMessage optional error details
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId,
                    String action,
                    String resourceType,
                    UUID resourceId,
                    boolean success,
                    Map<String, Object> details,
                    String errorMessage) {
        logInternal(userId, action, resourceType, resourceId, success, details, errorMessage);
    }

    private void logInternal(UUID userId,
                             String action,
                             String resourceType,
                             UUID resourceId,
                             boolean success,
                             Map<String, Object> details,
                             String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .userEmail(resolveUserEmail(userId))
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(resolveIpAddress())
                    .userAgent(resolveUserAgent())
                    .details(details != null ? new LinkedHashMap<>(details) : Map.of())
                    .success(success)
                    .errorMessage(errorMessage)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Не удалось сохранить audit log для action={}: {}", action, ex.getMessage());
        }
    }

    private String resolveUserEmail(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(user -> user.getEmail()).orElse(null);
    }

    private String resolveIpAddress() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
