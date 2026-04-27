package ru.normacontrol.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistence entity for strategy enablement settings.
 */
@Entity
@Table(name = "check_strategy_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckStrategySettingJpaEntity {

    @Id
    @Column(name = "strategy_code", nullable = false, length = 32)
    private String strategyCode;

    @Column(nullable = false)
    private boolean enabled;
}
