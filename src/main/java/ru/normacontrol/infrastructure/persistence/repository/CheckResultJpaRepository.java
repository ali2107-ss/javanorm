package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckResultJpaRepository extends JpaRepository<CheckResultJpaEntity, UUID> {
    List<CheckResultJpaEntity> findByDocumentId(UUID documentId);

    @Query("SELECT cr FROM CheckResultJpaEntity cr WHERE cr.documentId = :documentId ORDER BY cr.checkedAt DESC LIMIT 1")
    Optional<CheckResultJpaEntity> findLatestByDocumentId(UUID documentId);
}
