package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.ViolationJpaEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for violation analytics.
 */
@Repository
public interface ViolationJpaRepository extends JpaRepository<ViolationJpaEntity, UUID> {

    /**
     * Projection for top violation rows.
     */
    interface TopViolationProjection {
        String getRuleCode();
        long getCount();
        String getDescription();
    }

    /**
     * Return the most frequent violations.
     *
     * @return top ten grouped violations
     */
    @Query(value = """
            select v.rule_code as ruleCode,
                   count(*) as count,
                   min(v.description) as description
            from violations v
            group by v.rule_code
            order by count(*) desc
            limit 10
            """, nativeQuery = true)
    List<TopViolationProjection> findTopViolations();
}
