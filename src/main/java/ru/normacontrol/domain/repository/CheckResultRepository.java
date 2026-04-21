package ru.normacontrol.domain.repository;

import ru.normacontrol.domain.entity.CheckResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт репозитория результатов проверки (Domain layer).
 */
public interface CheckResultRepository {
    CheckResult save(CheckResult checkResult);
    Optional<CheckResult> findById(UUID id);
    List<CheckResult> findByDocumentId(UUID documentId);
    Optional<CheckResult> findLatestByDocumentId(UUID documentId);
}
