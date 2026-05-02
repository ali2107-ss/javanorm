package ru.normacontrol.infrastructure.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.UserRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.infrastructure.audit.AuditService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WriteDocumentRepository documentRepository;
    private final CheckResultRepository checkResultRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        boolean freshDemoData = !userRepository.existsByEmail("admin@demo.ru")
                && !userRepository.existsByEmail("user@demo.ru");

        User admin = createUserIfMissing("admin@demo.ru", "admin", "Admin1234!", "Администратор", RoleName.ROLE_ADMIN);
        User user = createUserIfMissing("user@demo.ru", "user", "User1234!", "Пользователь", RoleName.ROLE_USER);

        if (!freshDemoData) {
            log.info("Demo users already exist, skipping demo documents and audit log initialization.");
            return;
        }

        createDocumentWithResult("ТЗ_НормаКонтроль.docx", DocumentStatus.CHECKED, true, 94, user.getId(), null);
        createDocumentWithResult("Пояснительная_записка.docx", DocumentStatus.CHECKED, true, 87, user.getId(), null);
        createDocumentWithResult("Руководство_пользователя.docx", DocumentStatus.CHECKED, false, 72, user.getId(), null);
        createDocumentWithResult("ТЗ_версия_2.docx", DocumentStatus.CHECKED, false, 61, user.getId(), null);
        createDocumentWithResult("Черновик_ТЗ.docx", DocumentStatus.FAILED, false, 43, user.getId(), createViolations());

        generateAuditLogs(user, admin);
        log.info("Demo data initialized: admin@demo.ru and user@demo.ru.");
    }

    private User createUserIfMissing(String email, String username, String password, String fullName, RoleName roleName) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> createUser(email, username, password, fullName, roleName));
    }

    private User createUser(String email, String username, String password, String fullName, RoleName roleName) {
        Role role = Role.builder()
                .id((long) (roleName.ordinal() + 1))
                .name(roleName)
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .roles(Set.of(role))
                .build();

        return userRepository.save(user);
    }

    private void createDocumentWithResult(String filename,
                                          DocumentStatus status,
                                          boolean passed,
                                          int score,
                                          UUID ownerId,
                                          Set<Violation> explicitViolations) {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .originalFilename(filename)
                .storageKey(UUID.randomUUID() + "-" + filename)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize((long) (Math.random() * 5 * 1024 * 1024 + 1024))
                .status(status)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now().minusDays((long) (Math.random() * 30)))
                .updatedAt(LocalDateTime.now())
                .build();

        documentRepository.save(document);

        CheckResult checkResult = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(document.getId())
                .passed(passed)
                .complianceScore(score)
                .checkedAt(document.getCreatedAt().plusMinutes(2))
                .checkedBy(ownerId)
                .ruleSetName("ГОСТ 19.201-78")
                .ruleSetVersion("1.0")
                .processingTimeMs((long) (Math.random() * 5000 + 1000))
                .build();

        if (explicitViolations != null) {
            explicitViolations.forEach(checkResult::addViolation);
            checkResult.evaluate();
        } else if (score < 100) {
            checkResult.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode("FMT-001")
                    .description("Незначительное нарушение оформления")
                    .severity(ViolationSeverity.WARNING)
                    .pageNumber(1)
                    .ruleReference("ГОСТ 19.201-78")
                    .build());
            checkResult.evaluate();
        }

        checkResult.setComplianceScore(score);
        checkResult.setPassed(passed);
        checkResultRepository.save(checkResult);
    }

    private Set<Violation> createViolations() {
        return Set.of(
                createViolation("STRUCT-001", "Отсутствует раздел Введение", ViolationSeverity.CRITICAL, "ГОСТ 19.201.STRUCTURE.MISSING_SECTION"),
                createViolation("STRUCT-002", "Отсутствует раздел Назначение и цели", ViolationSeverity.CRITICAL, "ГОСТ 19.201.STRUCTURE.MISSING_SECTION"),
                createViolation("LANG-001", "Использована разговорная формулировка", ViolationSeverity.CRITICAL, "ГОСТ 19.201.LANGUAGE.FORBIDDEN_PHRASE"),
                createViolation("FMT-001", "Шрифт отличается от Times New Roman", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                createViolation("TBL-001", "Таблица без подписи", ViolationSeverity.WARNING, "ГОСТ 19.201.TABLE.MISSING_CAPTION"),
                createViolation("FMT-002", "Некорректный межстрочный интервал", ViolationSeverity.INFO, "ГОСТ 19.201.FORMAT.WRONG_ALIGNMENT")
        );
    }

    private Violation createViolation(String code, String desc, ViolationSeverity severity, String reference) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode(code)
                .description(desc)
                .severity(severity)
                .pageNumber((int) (Math.random() * 10 + 1))
                .ruleReference(reference)
                .suggestion("Исправьте в соответствии с ГОСТ")
                .build();
    }

    private void generateAuditLogs(User user, User admin) {
        User[] users = {user, admin};
        String[] actions = {"LOGIN", "UPLOAD_DOCUMENT", "START_CHECK", "VIEW_RESULT", "DOWNLOAD_REPORT"};

        for (int i = 0; i < 20; i++) {
            User actor = users[i % users.length];
            String action = actions[i % actions.length];
            auditService.log(
                    actor.getId(),
                    action,
                    "SYSTEM",
                    UUID.randomUUID(),
                    true,
                    Map.of("demo", true, "sequence", i + 1)
            );
        }
    }
}
