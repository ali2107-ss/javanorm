package ru.normacontrol.domain.repository;

import ru.normacontrol.domain.entity.User;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт репозитория пользователей (Domain layer).
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    void deleteById(UUID id);
}
