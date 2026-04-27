package ru.normacontrol.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.infrastructure.persistence.entity.AuditLogJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.AuditLogJpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists audit records in a separate transaction.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogJpaRepository auditLogJpaRepository;

    /**
     * Save audit record in an independent transaction.
     *
     * @param userId user identifier
     * @param userEmail user email
     * @param action action code
     * @param resourceType resource type
     * @param resourceId resource identifier
     * @param details JSON details payload
     * @param success success flag
     * @param errorMessage optional error message
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId,
                    String userEmail,
                    String action,
                    String resourceType,
                    UUID resourceId,
                    String details,
                    boolean success,
                    String errorMessage) {
        auditLogJpaRepository.save(AuditLogJpaEntity.builder()
                .userId(userId)
                .userEmail(userEmail)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .success(success)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
