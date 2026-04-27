package ru.normacontrol.domain.checker.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.normacontrol.infrastructure.persistence.repository.CheckStrategySettingJpaRepository;

/**
 * Resolves strategy enablement flags from the database.
 */
@Service
@RequiredArgsConstructor
public class CheckStrategySettingsService {

    private final CheckStrategySettingJpaRepository repository;

    /**
     * Resolve whether a strategy is enabled.
     *
     * @param strategyCode unique strategy code
     * @return {@code true} when enabled or absent in configuration
     */
    public boolean isEnabled(String strategyCode) {
        return repository.findById(strategyCode)
                .map(setting -> setting.isEnabled())
                .orElse(true);
    }
}
