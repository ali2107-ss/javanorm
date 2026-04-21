package ru.normacontrol.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.infrastructure.persistence.entity.RoleJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.RoleJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Адаптер, реализующий доменный порт UserRepository
 * через Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    @Override
    public User save(User user) {
        UserJpaEntity entity = toJpaEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    // ── Маппинг Domain ↔ JPA ──────────────────────────────────────────────

    private UserJpaEntity toJpaEntity(User user) {
        Set<RoleJpaEntity> roleEntities = user.getRoles().stream()
                .map(role -> roleJpaRepository.findByName(role.getName())
                        .orElseGet(() -> RoleJpaEntity.builder()
                                .id(role.getId())
                                .name(role.getName())
                                .build()))
                .collect(Collectors.toSet());

        return UserJpaEntity.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .fullName(user.getFullName())
                .oauthProvider(user.getOauthProvider())
                .oauthId(user.getOauthId())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(roleEntities)
                .build();
    }

    private User toDomain(UserJpaEntity entity) {
        Set<Role> roles = entity.getRoles().stream()
                .map(r -> Role.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .build())
                .collect(Collectors.toSet());

        return User.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .fullName(entity.getFullName())
                .oauthProvider(entity.getOauthProvider())
                .oauthId(entity.getOauthId())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .roles(roles)
                .build();
    }
}
