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
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.messaging.CheckEventPublisher;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.websocket.ProgressPublisher;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Use Case: Непосредственная проверка документа на соответствие ГОСТ 19.201-78.
 * Выполняется асинхронно в отдельном пуле потоков.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;
    private final GostRuleEngine gostRuleEngine;
    private final CheckResultMapper checkResultMapper;
    
    private final ProgressPublisher progressPublisher;
    private final CheckEventPublisher checkEventPublisher;

    /**
     * Выполнить проверку документа асинхронно.
     * Возвращает CompletableFuture, чтобы вызывающий Consumer мог дождаться результата
     * и при необходимости инициировать Retry в Kafka при ошибке.
     *
     * @param documentId UUID документа
     * @param checkedBy  UUID пользователя, инициировавшего проверку
     * @return Future
     */
    @Async("checkExecutor")
    @Transactional
    public CompletableFuture<Void> executeCheck(UUID documentId, UUID checkedBy) {
        log.info("Начало проверки документа (Async): {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));

        document.startChecking();
        documentRepository.save(document);

        try {
            // ШАГ 1: Скачивание
            progressPublisher.publishProgress(documentId, 10, "DOWNLOADING", "Скачивание файла из хранилища");
            InputStream fileStream = storageService.downloadFile(document.getStorageKey());

            // ШАГ 2: Парсинг
            progressPublisher.publishProgress(documentId, 30, "PARSING", "Чтение структуры и содержимого DOCX");
            XWPFDocument xwpfDocument;
            String contentType = document.getContentType();
            if (contentType != null && (contentType.contains("wordprocessingml") || contentType.contains("msword"))) {
                xwpfDocument = new XWPFDocument(fileStream);
            } else {
                throw new IllegalArgumentException("Поддерживается только формат DOCX.");
            }

            // ШАГ 3: Проверка
            progressPublisher.publishProgress(documentId, 60, "CHECKING", "Анализ документа движком ГОСТ 19.201-78");
            CheckResult result;
            try (xwpfDocument) {
                result = gostRuleEngine.check(xwpfDocument, documentId, checkedBy);
            }

            // ШАГ 4: Генерация отчёта
            progressPublisher.publishProgress(documentId, 85, "GENERATING_REPORT", "Сохранение результатов проверки");
            checkResultRepository.save(result);
            document.markChecked();
            documentRepository.save(document);

            // ШАГ 5: Готово
            progressPublisher.publishProgress(documentId, 100, "DONE", "Проверка успешно завершена");
            
            // Публикация события завершения в Kafka
            List<String> violationCodes = result.getViolations().stream()
                    .map(v -> v.getRuleCode())
                    .collect(Collectors.toList());
                    
            // Оценка (условная логика: 100 минус штрафы)
            int score = Math.max(0, 100 - (result.getTotalViolations() * 5));
            
            checkEventPublisher.publishCheckCompleted(documentId, score, result.isPassed(), violationCodes);
            
            log.info("Проверка документа {} завершена. Нарушений: {}", documentId, result.getTotalViolations());

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Ошибка при проверке документа {}: {}", documentId, e.getMessage(), e);
            document.markFailed();
            documentRepository.save(document);
            
            progressPublisher.publishProgress(documentId, 0, "FAILED", "Ошибка при проверке: " + e.getMessage());
            
            // Пробрасываем исключение, чтобы Kafka Consumer мог сделать Retry
            return CompletableFuture.failedFuture(new RuntimeException("Ошибка при проверке документа: " + e.getMessage(), e));
        }
    }

    @Transactional(readOnly = true)
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Результат проверки не найден для документа: " + documentId));
        return checkResultMapper.toResponse(result);
    }
}
