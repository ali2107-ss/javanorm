package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.mapper.CheckResultMapper;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

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
    private final GostRuleEngine gostRuleEngine;
    private final CheckResultMapper checkResultMapper;

    /**
     * Выполнить проверку документа.
     *
     * @param documentId UUID документа
     * @param checkedBy  UUID пользователя, инициировавшего проверку
     * @return результат проверки
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

            // Открываем как XWPFDocument (DOCX)
            XWPFDocument xwpfDocument;
            String contentType = document.getContentType();
            if (contentType != null && (contentType.contains("wordprocessingml")
                    || contentType.contains("msword"))) {
                xwpfDocument = new XWPFDocument(fileStream);
            } else if (contentType != null && contentType.contains("pdf")) {
                // PDF пока не поддерживается напрямую движком ГОСТ
                // (нужна конвертация PDF → DOCX или отдельный парсер)
                throw new UnsupportedOperationException(
                        "Проверка ГОСТ для PDF в разработке. Загрузите документ в формате DOCX.");
            } else {
                throw new IllegalArgumentException("Неподдерживаемый формат: " + contentType);
            }

            // Запуск GostRuleEngine (Chain of Responsibility)
            CheckResult result;
            try (xwpfDocument) {
                result = gostRuleEngine.check(xwpfDocument, documentId, checkedBy);
            }

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
     *
     * @param documentId UUID документа
     * @return последний результат проверки
     */
    @Transactional(readOnly = true)
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Результат проверки не найден для документа: " + documentId));
        return checkResultMapper.toResponse(result);
    }
}
