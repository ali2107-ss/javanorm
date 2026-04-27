package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
import ru.normacontrol.infrastructure.ai.AiRecommendationService;
import ru.normacontrol.infrastructure.messaging.CheckEventPublisher;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
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
    private final CheckEventPublisher checkEventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Execute the check asynchronously.
     *
     * @param documentId document identifier
     * @param checkedBy user who started the check
     * @return completion future
     */
    @Async("checkExecutor")
    @Transactional
    public CompletableFuture<Void> executeCheck(UUID documentId, UUID checkedBy) {
        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));

        document.startChecking();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(new CheckStartedEvent(documentId, "GOST 19.201-78"));

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
                        .map(v -> aiRecommendationService.generateRecommendation(v, "Не указан")
                                .thenAccept(v::setAiSuggestion))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            checkResultRepository.save(result);
            document.markChecked();
            writeDocumentRepository.save(document);
            domainEventPublisher.publish(result.attachResult());

            int score = result.calculateScore();
            List<String> violationCodes = result.getViolations().stream().map(v -> v.getRuleCode()).toList();
            checkEventPublisher.publishCheckCompleted(documentId, score, result.isPassed(), violationCodes);
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
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Результат проверки не найден: " + documentId));
        return checkResultMapper.toResponse(result);
    }
}
