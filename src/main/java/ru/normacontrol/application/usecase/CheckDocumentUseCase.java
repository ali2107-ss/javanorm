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
import ru.normacontrol.application.mapper.CheckResultMapper;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.event.CheckStartedEvent;
import ru.normacontrol.domain.event.DomainEventPublisher;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.application.event.DocumentCheckRequestedEvent;
import ru.normacontrol.infrastructure.ai.AiRecommendationService;
import ru.normacontrol.infrastructure.audit.AuditLogged;
import ru.normacontrol.infrastructure.kafka.producer.DocumentCheckProducer;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.report.ReportGenerator;
import ru.normacontrol.infrastructure.websocket.ProgressPublisher;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case that performs a full document check.
 */
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
    private final DocumentCheckProducer documentCheckProducer;
    private final DomainEventPublisher domainEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReportGenerator reportGenerator;

    /**
     * Mark the document as queued and schedule Kafka publication after commit.
     *
     * @param documentId document identifier
     * @param requestedBy requesting user
     */
    @Transactional
    @AuditLogged(action = "START_CHECK", resourceType = "DOCUMENT")
    public void initiateCheck(UUID documentId, UUID requestedBy) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));
        document.enqueue();
        writeDocumentRepository.save(document);
        applicationEventPublisher.publishEvent(new DocumentCheckRequestedEvent(documentId, requestedBy));
    }

    /**
     * Execute the check asynchronously.
     *
     * @param documentId document identifier
     * @param checkedBy user who started the check
     * @return completion future
     */
    @Async("checkExecutor")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CompletableFuture<Void> executeCheck(UUID documentId, UUID checkedBy) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));

        document.startChecking();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(new CheckStartedEvent(documentId, "ГОСТ 19.201-78"));

        long startedAt = System.currentTimeMillis();
        try (InputStream fileStream = storageService.downloadFile(document.getStorageKey());
             XWPFDocument xwpfDocument = new XWPFDocument(fileStream)) {

            progressPublisher.publishProgress(documentId, 60, "CHECKING", "Проверка документа");
            CheckResult result = gostRuleEngine.check(xwpfDocument, documentId, checkedBy);

            long criticalCount = result.getViolations().stream()
                    .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                    .count();
            if (criticalCount > 0) {
                List<CompletableFuture<Void>> futures = result.getViolations().stream()
                        .filter(v -> v.getSeverity() == ViolationSeverity.CRITICAL)
                        .map(v -> aiRecommendationService.generateRecommendation(v, "Не указан").thenAccept(v::setAiSuggestion))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            result.setRuleSetName("ГОСТ 19.201-78");
            result.setRuleSetVersion("1.0");
            result.setProcessingTimeMs(System.currentTimeMillis() - startedAt);
            result.evaluate();

            String reportPath = reportGenerator.generatePdfReport(result, document);
            result.setReportStoragePath(reportPath);

            checkResultRepository.save(result);
            document.markChecked();
            writeDocumentRepository.save(document);
            domainEventPublisher.publish(result.attachResult());

            progressPublisher.publishProgress(documentId, 100, "DONE", "Проверка завершена");

            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            document.markFailed();
            writeDocumentRepository.save(document);
            progressPublisher.publishProgress(documentId, 0, "FAILED", "Ошибка при проверке: " + ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Get latest saved check result for the document.
     *
     * @param documentId document identifier
     * @return latest result DTO
     */
    @Transactional(readOnly = true)
    @AuditLogged(action = "VIEW_RESULT", resourceType = "CHECK_RESULT")
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Результат проверки не найден: " + documentId));
        return checkResultMapper.toResponse(result);
    }
}
