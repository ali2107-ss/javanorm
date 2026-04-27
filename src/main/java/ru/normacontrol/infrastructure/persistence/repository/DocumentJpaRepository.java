package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    List<DocumentJpaEntity> findByOwner_IdAndDeletedFalse(UUID ownerId);
    List<DocumentJpaEntity> findByStatusAndDeletedFalse(DocumentStatus status);
    long countByDeletedFalse();

    @Query("""
            SELECT d FROM DocumentJpaEntity d
            LEFT JOIN FETCH d.owner
            WHERE d.owner.id = :ownerId AND d.deleted = false
            """)
    Page<DocumentJpaEntity> findByOwnerWithOwner(UUID ownerId, Pageable pageable);
}
