package ru.normacontrol.infrastructure.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.domain.entity.*;
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
    public void run(String... args) throws Exception {
        log.info("Демо-профиль активирован. Инициализация тестовых данных...");

        if (userRepository.existsByEmail("admin@normacontrol.ru")) {
            log.info("Тестовые данные уже существуют.");
            return;
        }

        // 1. Создание пользователей
        User admin = createUser("admin@normacontrol.ru", "admin", "Admin1234!", "Администратор", RoleName.ROLE_ADMIN);
        User reviewer = createUser("reviewer@normacontrol.ru", "reviewer", "Review123!", "Рецензент", RoleName.ROLE_REVIEWER);
        User student = createUser("student@normacontrol.ru", "student", "Student123!", "Идаят Али", RoleName.ROLE_USER);

        // 2. Создание документов
        createDocumentWithResult("ТЗ_НормаКонтроль.docx", DocumentStatus.DONE, true, 87, student.getId(), null);
        createDocumentWithResult("Пояснительная_записка.docx", DocumentStatus.DONE, false, 62, student.getId(), null);
        createDocumentWithResult("Руководство_пользователя.docx", DocumentStatus.DONE, true, 94, student.getId(), null);
        createDocumentWithResult("ТЗ_версия2.docx", DocumentStatus.DONE, true, 91, student.getId(), null);
        
        // Документ с детальными нарушениями
        createDocumentWithResult("Черновик_ТЗ.docx", DocumentStatus.FAILED, false, 43, student.getId(), createViolations());

        // 3. Создание Audit Log
        generateAuditLogs(student, admin, reviewer);

        log.info("Инициализация тестовых данных завершена успешно.");
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

    private void createDocumentWithResult(String filename, DocumentStatus status, boolean passed, int score, UUID ownerId, Set<Violation> explicitViolations) {
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .originalFilename(filename)
                .storageKey(UUID.randomUUID() + "-" + filename)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize((long) (Math.random() * 5 * 1024 * 1024 + 1024))
                .status(status)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now().minusDays((long) (Math.random() * 30)))
                .build();
        
        documentRepository.save(document);

        if (status == DocumentStatus.DONE || status == DocumentStatus.FAILED) {
            CheckResult checkResult = CheckResult.builder()
                    .id(UUID.randomUUID())
                    .documentId(document.getId())
                    .passed(passed)
                    .complianceScore(score)
                    .checkedAt(document.getCreatedAt().plusMinutes(2))
                    .checkedBy(UUID.randomUUID())
                    .ruleSetName("ГОСТ 19.201-78")
                    .ruleSetVersion("1.0")
                    .processingTimeMs((long) (Math.random() * 5000 + 1000))
                    .build();

            if (explicitViolations != null) {
                explicitViolations.forEach(checkResult::addViolation);
                checkResult.evaluate();
            } else {
                // Generate a dummy violation just to have something if score < 100
                if (score < 100) {
                    checkResult.addViolation(Violation.builder()
                            .id(UUID.randomUUID())
                            .ruleCode("FMT-001")
                            .description("Незначительное нарушение формата")
                            .severity(ViolationSeverity.WARNING)
                            .pageNumber(1)
                            .ruleReference("ГОСТ 19.201-78")
                            .build());
                    checkResult.evaluate();
                    checkResult.setComplianceScore(score); // override calculated score
                }
            }
            
            checkResultRepository.save(checkResult);
        }
    }

    private Set<Violation> createViolations() {
        return Set.of(
                createViolation("STRUCT-001", "Отсутствует раздел «Введение»", ViolationSeverity.CRITICAL, "ГОСТ 19.201.STRUCTURE.MISSING_SECTION"),
                createViolation("STRUCT-002", "Отсутствует раздел «Назначение и цели»", ViolationSeverity.CRITICAL, "ГОСТ 19.201.STRUCTURE.MISSING_SECTION"),
                createViolation("STRUCT-003", "Отсутствует раздел «Требования к программе»", ViolationSeverity.CRITICAL, "ГОСТ 19.201.STRUCTURE.MISSING_SECTION"),
                
                createViolation("LANG-001", "Использование недопустимой фразы (разговорный стиль)", ViolationSeverity.CRITICAL, "ГОСТ 19.201.LANGUAGE.FORBIDDEN_PHRASE"),
                createViolation("LANG-002", "Наличие неопределенного термина", ViolationSeverity.CRITICAL, "ГОСТ 19.201.LANGUAGE.FORBIDDEN_PHRASE"),
                
                createViolation("FMT-001", "Шрифт отличается от Times New Roman", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                createViolation("FMT-002", "Размер шрифта менее 14 пт", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                createViolation("FMT-003", "Заголовок не выделен полужирным", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                createViolation("FMT-004", "Некорректный междустрочный интервал", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                createViolation("FMT-005", "Цвет текста отличается от черного", ViolationSeverity.WARNING, "ГОСТ 19.201.FORMAT.WRONG_FONT"),
                
                createViolation("TBL-001", "Таблица без подписи", ViolationSeverity.WARNING, "ГОСТ 19.201.TABLE.MISSING_CAPTION"),
                createViolation("TBL-002", "Таблица 2 без подписи", ViolationSeverity.WARNING, "ГОСТ 19.201.TABLE.MISSING_CAPTION"),
                
                createViolation("FMT-006", "Текст не выровнен по ширине", ViolationSeverity.INFO, "ГОСТ 19.201.FORMAT.WRONG_ALIGNMENT"),
                createViolation("FMT-007", "Отсутствует абзацный отступ", ViolationSeverity.INFO, "ГОСТ 19.201.FORMAT.WRONG_ALIGNMENT"),
                createViolation("FMT-008", "Нарушение полей страницы", ViolationSeverity.INFO, "ГОСТ 19.201.FORMAT.WRONG_ALIGNMENT")
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

    private void generateAuditLogs(User u1, User u2, User u3) {
        User[] users = {u1, u2, u3};
        String[] actions = {"LOGIN", "UPLOAD_DOCUMENT", "START_CHECK", "VIEW_RESULT", "DOWNLOAD_REPORT"};
        
        for (int i = 0; i < 50; i++) {
            User user = users[i % users.length];
            String action = actions[(int) (Math.random() * actions.length)];
            
            // We can't use auditService.log easily synchronously with random dates,
            // because it sets time = now(). So we just use it and accept recent timestamps,
            // or we'd have to use AuditLogRepository directly if we needed exact past dates.
            // But auditService will do for recent "last 30 days" demo.
            auditService.log(
                    user.getId(),
                    action,
                    "SYSTEM",
                    UUID.randomUUID(),
                    true,
                    Map.of("demo", "true", "iter", i)
            );
        }
    }
}
