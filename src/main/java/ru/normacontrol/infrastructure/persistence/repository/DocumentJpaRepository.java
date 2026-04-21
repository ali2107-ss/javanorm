package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    List<DocumentJpaEntity> findByOwnerId(UUID ownerId);
    List<DocumentJpaEntity> findByStatus(DocumentStatus status);
}
