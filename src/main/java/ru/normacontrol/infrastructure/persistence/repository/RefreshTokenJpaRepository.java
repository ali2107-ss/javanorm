package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.infrastructure.persistence.entity.RefreshTokenJpaEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {
    Optional<RefreshTokenJpaEntity> findByToken(String token);
    void deleteByUserId(UUID userId);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
