package ru.normacontrol.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.infrastructure.persistence.entity.RoleJpaEntity;

import java.util.Optional;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {
    Optional<RoleJpaEntity> findByName(RoleName name);
}
