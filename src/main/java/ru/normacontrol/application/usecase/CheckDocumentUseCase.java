package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.mapper.CheckResultMapper;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.domain.service.GostRuleEngine.DocumentMetadata;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.parser.DocumentParser;
import ru.normacontrol.infrastructure.parser.DocumentParser.ParsedDocument;

import java.io.InputStream;
import java.util.UUID;

/**
 * Use Case: Непосредственная проверка документа на соответствие ГОСТ 19.201-78.
 * Вызывается из Kafka Consumer при обработке очереди.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;
    private final DocumentParser documentParser;
    private final GostRuleEngine gostRuleEngine;
    private final CheckResultMapper checkResultMapper;

    /**
     * Выполнить проверку документа.
     */
    @Transactional
    public CheckResultResponse executeCheck(UUID documentId, UUID checkedBy) {
        log.info("Начало проверки документа: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Документ не найден: " + documentId));

        document.startChecking();
        documentRepository.save(document);

        try {
            // Скачивание файла из MinIO
            InputStream fileStream = storageService.downloadFile(document.getStorageKey());

            // Парсинг документа (DOCX или PDF)
            ParsedDocument parsed = documentParser.parse(fileStream, document.getContentType());

            // Формирование метаданных для проверки
            DocumentMetadata metadata = new DocumentMetadata(
                    parsed.fontSize(),
                    parsed.fontName(),
                    parsed.marginLeft(),
                    parsed.marginRight(),
                    parsed.marginTop(),
                    parsed.marginBottom(),
                    parsed.lineSpacing(),
                    parsed.pageCount(),
                    parsed.hasPageNumbers()
            );

            // Запуск GostRuleEngine
            CheckResult result = gostRuleEngine.check(
                    parsed.text(), metadata, documentId, checkedBy);

            // Сохранение результата
            checkResultRepository.save(result);

            // Обновление статуса документа
            document.markChecked();
            documentRepository.save(document);

            log.info("Проверка документа {} завершена. Нарушений: {}",
                    documentId, result.getTotalViolations());

            return checkResultMapper.toResponse(result);

        } catch (Exception e) {
            log.error("Ошибка при проверке документа {}: {}", documentId, e.getMessage(), e);
            document.markFailed();
            documentRepository.save(document);
            throw new RuntimeException("Ошибка при проверке документа: " + e.getMessage(), e);
        }
    }

    /**
     * Получить последний результат проверки документа.
     */
    @Transactional(readOnly = true)
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Результат проверки не найден для документа: " + documentId));
        return checkResultMapper.toResponse(result);
    }
}
