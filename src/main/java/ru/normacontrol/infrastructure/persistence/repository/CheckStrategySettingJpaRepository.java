package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.CheckStrategySettingJpaEntity;

/**
 * Repository for strategy settings.
 */
@Repository
public interface CheckStrategySettingJpaRepository extends JpaRepository<CheckStrategySettingJpaEntity, String> {
}
