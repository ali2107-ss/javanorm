package ru.normacontrol.infrastructure.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.infrastructure.persistence.entity.RoleJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.RoleJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        RoleJpaEntity userRole = createRoleIfMissing(RoleName.ROLE_USER);
        RoleJpaEntity adminRole = createRoleIfMissing(RoleName.ROLE_ADMIN);

        createUserIfMissing("admin@demo.ru", "Administrator", "Admin1234!", Set.of(adminRole, userRole));
        createUserIfMissing("user@demo.ru", "Student", "User1234!", Set.of(userRole));

        cleanupDemoSeedData();
    }

    private RoleJpaEntity createRoleIfMissing(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(RoleJpaEntity.builder().name(roleName).build()));
    }

    private UserJpaEntity createUserIfMissing(String email, String displayName, String password, Set<RoleJpaEntity> roles) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(UserJpaEntity.builder()
                .id(UUID.randomUUID())
                .email(email)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(password))
                .enabled(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .roles(roles)
                .build()));
    }

    private void cleanupDemoSeedData() {
        try {
            int resetDocuments = jdbcTemplate.update("""
                    UPDATE documents
                    SET status = 'UPLOADED', updated_at = NOW()
                    WHERE storage_path NOT LIKE 'demo/%'
                      AND id IN (
                        SELECT document_id FROM check_results
                        WHERE report_storage_path LIKE 'demo/%'
                      )
                    """);
            int deletedViolations = jdbcTemplate.update("""
                    DELETE FROM violations
                    WHERE check_result_id IN (
                        SELECT id FROM check_results
                        WHERE report_storage_path LIKE 'demo/%'
                    )
                    """);
            int deletedResults = jdbcTemplate.update(
                    "DELETE FROM check_results WHERE report_storage_path LIKE 'demo/%'");
            deletedViolations += jdbcTemplate.update("""
                    DELETE FROM violations
                    WHERE check_result_id IN (
                        SELECT cr.id
                        FROM check_results cr
                        JOIN documents d ON d.id = cr.document_id
                        WHERE d.storage_path LIKE 'demo/%'
                    )
                    """);
            deletedResults += jdbcTemplate.update("""
                    DELETE FROM check_results
                    WHERE document_id IN (
                        SELECT id FROM documents WHERE storage_path LIKE 'demo/%'
                    )
                    """);
            int deletedDocuments = jdbcTemplate.update(
                    "DELETE FROM documents WHERE storage_path LIKE 'demo/%'");
            int deletedAudit = jdbcTemplate.update(
                    "DELETE FROM audit_logs WHERE details ->> 'demo' = 'true'");

            if (resetDocuments + deletedViolations + deletedResults + deletedDocuments + deletedAudit > 0) {
                log.info("Demo seed data cleaned: resetDocuments={}, deletedViolations={}, deletedResults={}, deletedDocuments={}, deletedAudit={}",
                        resetDocuments, deletedViolations, deletedResults, deletedDocuments, deletedAudit);
            }
        } catch (Exception e) {
            log.warn("Could not clean old demo seed data: {}", e.getMessage());
        }
    }
}
