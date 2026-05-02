package ru.normacontrol.infrastructure.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.infrastructure.audit.AuditService;
import ru.normacontrol.infrastructure.persistence.entity.CheckResultJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.DocumentJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.RoleJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.ViolationJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.CheckResultJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.DocumentJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.RoleJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final DocumentJpaRepository documentRepository;
    private final CheckResultJpaRepository checkResultRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        RoleJpaEntity userRole = createRoleIfMissing(RoleName.ROLE_USER);
        RoleJpaEntity adminRole = createRoleIfMissing(RoleName.ROLE_ADMIN);

        if (userRepository.count() == 0 || userRepository.findByEmail("admin@demo.ru").isEmpty()) {
            createUserIfMissing("admin@demo.ru", "Администратор", "Admin1234!", Set.of(adminRole, userRole));
        }
        if (userRepository.count() == 0 || userRepository.findByEmail("user@demo.ru").isEmpty()) {
            createUserIfMissing("user@demo.ru", "Студент", "User1234!", Set.of(userRole));
        }

        UserJpaEntity adminUser = userRepository.findByEmail("admin@demo.ru")
                .orElseThrow(() -> new IllegalStateException("Demo admin was not created"));

        if (documentRepository.countByDeletedFalse() == 0) {
            createDocumentWithResult(adminUser, "ТЗ_НормаКонтроль.docx", "demo/tz_normacontrol.docx", 245760L, 87, true);
            createDocumentWithResult(adminUser, "Пояснительная_записка.docx", "demo/poyasnitelnaya_zapiska.docx", 193536L, 62, false);
            createDocumentWithResult(adminUser, "Руководство_пользователя.docx", "demo/rukovodstvo_polzovatelya.docx", 319488L, 94, true);
            createDocumentWithResult(adminUser, "ТЗ_версия2.docx", "demo/tz_versiya2.docx", 262144L, 91, true);
            createDocumentWithResult(adminUser, "Черновик.docx", "demo/chernovik.docx", 151552L, 43, false);
            generateAuditLogs(adminUser);
            log.info("Demo documents and check results initialized.");
        }
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

    private void createDocumentWithResult(UserJpaEntity owner,
                                          String originalFileName,
                                          String storagePath,
                                          long fileSizeBytes,
                                          int score,
                                          boolean passed) {
        DocumentJpaEntity document = documentRepository.save(DocumentJpaEntity.builder()
                .id(UUID.randomUUID())
                .originalFileName(originalFileName)
                .storagePath(storagePath)
                .type("DOCX")
                .status(DocumentStatus.CHECKED)
                .fileSizeBytes(fileSizeBytes)
                .owner(owner)
                .deleted(false)
                .createdAt(LocalDateTime.now().minusDays(5 - Math.max(1, score % 5)))
                .updatedAt(LocalDateTime.now())
                .build());

        CheckResultJpaEntity result = CheckResultJpaEntity.builder()
                .id(UUID.randomUUID())
                .document(document)
                .ruleSetName("ГОСТ 19.201-78")
                .ruleSetVersion("1.0")
                .complianceScore(score)
                .passed(passed)
                .processingTimeMs(14500L)
                .checkedAt(LocalDateTime.now().minusMinutes(30))
                .reportStoragePath("demo/report_" + document.getId() + ".pdf")
                .build();

        List<ViolationJpaEntity> violations = new ArrayList<>();
        violations.add(createViolation(result,
                "GOST19.201.STRUCTURE.MISSING_SECTION",
                "Отсутствует раздел «ОСНОВАНИЯ ДЛЯ РАЗРАБОТКИ»",
                ViolationSeverity.CRITICAL,
                1,
                "Добавьте раздел согласно ГОСТ 19.201-78 п.2",
                "ГОСТ 19.201-78, раздел 2"));
        violations.add(createViolation(result,
                "GOST19.201.FORMAT.WRONG_FONT",
                "Неверный шрифт Calibri на странице 3",
                ViolationSeverity.WARNING,
                3,
                "Замените на Times New Roman 14pt",
                "ГОСТ 19.106-78, п.8.1"));
        if (!passed) {
            violations.add(createViolation(result,
                    "GOST19.201.LANGUAGE.FORBIDDEN_PHRASE",
                    "Запрещённая фраза «и т.д.» в тексте",
                    ViolationSeverity.CRITICAL,
                    4,
                    "Перечислите все пункты явно",
                    "ГОСТ 19.201-78"));
        }

        result.setViolations(violations);
        checkResultRepository.save(result);
    }

    private ViolationJpaEntity createViolation(CheckResultJpaEntity result,
                                               String ruleCode,
                                               String description,
                                               ViolationSeverity severity,
                                               int pageNumber,
                                               String suggestion,
                                               String ruleReference) {
        return ViolationJpaEntity.builder()
                .id(UUID.randomUUID())
                .checkResult(result)
                .ruleCode(ruleCode)
                .description(description)
                .severity(severity)
                .pageNumber(pageNumber)
                .lineNumber(0)
                .suggestion(suggestion)
                .aiSuggestion(suggestion)
                .ruleReference(ruleReference)
                .build();
    }

    private void generateAuditLogs(UserJpaEntity admin) {
        String[] actions = {"LOGIN", "UPLOAD_DOCUMENT", "START_CHECK", "VIEW_RESULT", "DOWNLOAD_REPORT"};
        for (int i = 0; i < 20; i++) {
            auditService.log(
                    admin.getId(),
                    actions[i % actions.length],
                    "SYSTEM",
                    UUID.randomUUID(),
                    true,
                    Map.of("demo", true, "sequence", i + 1)
            );
        }
    }
}
