package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);
    Optional<UserJpaEntity> findByDisplayName(String displayName);
    boolean existsByEmail(String email);
    boolean existsByDisplayName(String displayName);
}
