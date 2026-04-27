package ru.normacontrol.infrastructure.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for audit log queries and exports.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit entries using optional filters.
     *
     * @param userId optional user identifier
     * @param action optional action code
     * @param dateFrom optional range start
     * @param dateTo optional range end
     * @param pageable page request
     * @return matching audit page
     */
    @Query("""
            select a from AuditLog a
            where (:userId is null or a.userId = :userId)
              and (:action is null or a.action = :action)
              and (:dateFrom is null or a.timestamp >= :dateFrom)
              and (:dateTo is null or a.timestamp <= :dateTo)
            order by a.timestamp desc
            """)
    Page<AuditLog> findByFilters(@Param("userId") UUID userId,
                                 @Param("action") String action,
                                 @Param("dateFrom") LocalDateTime dateFrom,
                                 @Param("dateTo") LocalDateTime dateTo,
                                 Pageable pageable);

    /**
     * Find audit entries for CSV export.
     *
     * @param dateFrom optional range start
     * @param dateTo optional range end
     * @return filtered audit rows
     */
    @Query("""
            select a from AuditLog a
            where (:dateFrom is null or a.timestamp >= :dateFrom)
              and (:dateTo is null or a.timestamp <= :dateTo)
            order by a.timestamp desc
            """)
    List<AuditLog> findForExport(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);
}
