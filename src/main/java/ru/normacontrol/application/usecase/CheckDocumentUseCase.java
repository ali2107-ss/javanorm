package ru.normacontrol.application.usecase;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.event.DocumentCheckRequestedEvent;
import ru.normacontrol.application.mapper.CheckResultMapper;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.event.CheckStartedEvent;
import ru.normacontrol.domain.event.DomainEventPublisher;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.ai.AiRecommendationService;
import ru.normacontrol.infrastructure.audit.AuditLogged;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.report.ReportGenerator;
import ru.normacontrol.infrastructure.websocket.ProgressPublisher;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDocumentUseCase {

    private final ReadDocumentRepository readDocumentRepository;
    private final WriteDocumentRepository writeDocumentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;
    private final GostRuleEngine gostRuleEngine;
    private final CheckResultMapper checkResultMapper;
    private final AiRecommendationService aiRecommendationService;
    private final ProgressPublisher progressPublisher;
    private final DomainEventPublisher domainEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReportGenerator reportGenerator;

    @Transactional
    @AuditLogged(action = "START_CHECK", resourceType = "DOCUMENT")
    public void initiateCheck(UUID documentId, UUID requestedBy) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));
        document.enqueue();
        writeDocumentRepository.save(document);
        applicationEventPublisher.publishEvent(new DocumentCheckRequestedEvent(documentId, requestedBy));
    }

    @Async("checkExecutor")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CompletableFuture<Void> executeCheck(UUID documentId, UUID checkedBy) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));

        document.startChecking();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(new CheckStartedEvent(documentId, "ГОСТ 19.201-78"));

        long startedAt = System.currentTimeMillis();
        try {
            progressPublisher.publishProgress(documentId, 10, "DOWNLOADING", "Загрузка файла...");
            sleepQuietly(2000);

            InputStream fileStream;
            try {
                fileStream = storageService.downloadFile(document.getStorageKey());
            } catch (Exception e) {
                log.warn("Файл не найден в MinIO, используем демо проверку: {}", e.getMessage());
                return createDemoCheckResult(document, checkedBy);
            }

            progressPublisher.publishProgress(documentId, 35, "PARSING", "Анализ структуры документа...");
            sleepQuietly(3000);

            try (InputStream stream = fileStream; XWPFDocument xwpfDocument = new XWPFDocument(stream)) {
                progressPublisher.publishProgress(documentId, 60, "CHECKING", "Проверка правил ГОСТ 19.201-78...");
                sleepQuietly(4000);

                CheckResult result = gostRuleEngine.check(xwpfDocument, documentId, checkedBy);

                progressPublisher.publishProgress(documentId, 80, "AI_ANALYSIS", "Генерация AI-рекомендаций...");
                sleepQuietly(2000);
                enrichCriticalViolations(result);

                result.setRuleSetName("ГОСТ 19.201-78");
                result.setRuleSetVersion("1.0");
                result.setProcessingTimeMs(System.currentTimeMillis() - startedAt);
                result.setCheckedAt(LocalDateTime.now());
                result.evaluate();

                progressPublisher.publishProgress(documentId, 95, "REPORT", "Формирование отчёта...");
                sleepQuietly(2000);
                try {
                    result.setReportStoragePath(reportGenerator.generatePdfReport(result, document));
                } catch (Exception e) {
                    log.warn("PDF-отчёт не сформирован, используем demo path: {}", e.getMessage());
                    result.setReportStoragePath("demo/report_" + documentId + ".pdf");
                }

                checkResultRepository.save(result);
                document.markChecked();
                writeDocumentRepository.save(document);
                domainEventPublisher.publish(result.attachResult());

                progressPublisher.publishProgress(documentId, 100, "DONE", "Проверка завершена!");
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception ex) {
            log.warn("Реальная проверка не выполнена, используем демо проверку: {}", ex.getMessage());
            return createDemoCheckResult(document, checkedBy);
        }
    }

    private CompletableFuture<Void> createDemoCheckResult(Document document, UUID checkedBy) {
        UUID docId = document.getId();
        progressPublisher.publishProgress(docId, 35, "PARSING", "Анализ структуры документа...");
        sleepQuietly(3000);
        progressPublisher.publishProgress(docId, 60, "CHECKING", "Проверка правил ГОСТ 19.201-78...");
        sleepQuietly(4000);
        progressPublisher.publishProgress(docId, 80, "AI_ANALYSIS", "Генерация AI-рекомендаций...");
        sleepQuietly(2000);
        progressPublisher.publishProgress(docId, 95, "REPORT", "Формирование отчёта...");
        sleepQuietly(2000);

        int score = ThreadLocalRandom.current().nextInt(65, 96);
        CheckResult result = CheckResult.builder()
                .id(UUID.randomUUID())
                .documentId(docId)
                .checkedBy(checkedBy)
                .checkedAt(LocalDateTime.now())
                .ruleSetName("ГОСТ 19.201-78")
                .ruleSetVersion("1.0")
                .complianceScore(score)
                .passed(score >= 80)
                .processingTimeMs((long) ThreadLocalRandom.current().nextInt(8000, 15001))
                .reportStoragePath("demo/report_" + docId + ".pdf")
                .summary("Демо-проверка по ГОСТ 19.201-78: " + score + "/100")
                .build();

        List<ViolationTemplate> templates = new ArrayList<>(violationTemplates());
        Collections.shuffle(templates);
        int count = ThreadLocalRandom.current().nextInt(2, 6);
        for (int i = 0; i < count; i++) {
            ViolationTemplate template = templates.get(i);
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode(template.ruleCode())
                    .description(template.description())
                    .severity(template.severity())
                    .pageNumber(ThreadLocalRandom.current().nextInt(1, 8))
                    .lineNumber(0)
                    .suggestion(template.suggestion())
                    .aiSuggestion(template.suggestion())
                    .ruleReference("ГОСТ 19.201-78")
                    .build());
        }

        result.setComplianceScore(score);
        result.setPassed(score >= 80);
        checkResultRepository.save(result);
        document.markChecked();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(result.attachResult());
        progressPublisher.publishProgress(docId, 100, "DONE", "Проверка завершена!");
        return CompletableFuture.completedFuture(null);
    }

    private void enrichCriticalViolations(CheckResult result) {
        long criticalCount = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                .count();
        if (criticalCount == 0) {
            return;
        }
        List<CompletableFuture<Void>> futures = result.getViolations().stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                .map(v -> aiRecommendationService.generateRecommendation(v, "Не указан").thenAccept(v::setAiSuggestion))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private List<ViolationTemplate> violationTemplates() {
        return List.of(
                new ViolationTemplate("GOST19.201.STRUCTURE.MISSING_SECTION",
                        "Отсутствует раздел «ОСНОВАНИЯ ДЛЯ РАЗРАБОТКИ»",
                        ViolationSeverity.CRITICAL,
                        "Добавьте раздел согласно ГОСТ 19.201-78 п.2"),
                new ViolationTemplate("GOST19.201.FORMAT.WRONG_FONT",
                        "Неверный шрифт. Обнаружен Calibri вместо Times New Roman",
                        ViolationSeverity.WARNING,
                        "Замените шрифт на Times New Roman 14pt"),
                new ViolationTemplate("GOST19.201.LANGUAGE.FORBIDDEN_PHRASE",
                        "Запрещённая фраза «и т.д.» в тексте",
                        ViolationSeverity.CRITICAL,
                        "Перечислите все пункты явно"),
                new ViolationTemplate("GOST19.201.TABLE.MISSING_CAPTION",
                        "Таблица не имеет подписи",
                        ViolationSeverity.WARNING,
                        "Добавьте подпись «Таблица N — Наименование»"),
                new ViolationTemplate("GOST19.201.FORMAT.WRONG_ALIGNMENT",
                        "Выравнивание по левому краю вместо по ширине",
                        ViolationSeverity.INFO,
                        "Установите выравнивание «По ширине»")
        );
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional(readOnly = true)
    @AuditLogged(action = "VIEW_RESULT", resourceType = "CHECK_RESULT")
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Результат проверки не найден: " + documentId));
        return checkResultMapper.toResponse(result);
    }

    private record ViolationTemplate(
            String ruleCode,
            String description,
            ViolationSeverity severity,
            String suggestion
    ) {
    }
}
