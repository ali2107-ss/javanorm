package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckResultJpaRepository extends JpaRepository<CheckResultJpaEntity, UUID> {
    List<CheckResultJpaEntity> findByDocument_Id(UUID documentId);
    Optional<CheckResultJpaEntity> findFirstByDocument_IdOrderByCheckedAtDesc(UUID documentId);

    @EntityGraph(attributePaths = {"violations", "document"})
    Optional<CheckResultJpaEntity> findWithViolationsById(UUID id);

    long countByCheckedAtAfter(LocalDateTime since);

    @Query("select coalesce(avg(c.complianceScore), 0) from CheckResultJpaEntity c")
    Double findAverageComplianceScore();
}
