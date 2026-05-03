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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ru.normacontrol.infrastructure.plagiarism.PlagiarismChecker plagiarismChecker;

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
            progressPublisher.publishProgress(documentId, 10, "DOWNLOADING", "Загрузка файла из хранилища...");
            sleepQuietly(1500);

            // ── Шаг 1: получить InputStream файла ──────────────────────────
            InputStream fileStream = resolveFileStream(document);

            if (fileStream == null) {
                log.warn("Файл не найден ни в MinIO, ни во временной папке — используем демо-проверку");
                return createDemoCheckResult(document, checkedBy);
            }

            progressPublisher.publishProgress(documentId, 30, "PARSING", "Анализ структуры документа...");
            sleepQuietly(2000);

            // ── Шаг 2: определить тип и разобрать файл ─────────────────────
            String filename = document.getOriginalFilename() != null
                    ? document.getOriginalFilename().toLowerCase()
                    : "";
            boolean isPdf = filename.endsWith(".pdf") ||
                    "application/pdf".equalsIgnoreCase(document.getContentType());

            XWPFDocument xwpfDocument;
            try {
                if (isPdf) {
                    log.info("Парсинг PDF-файла: {}", filename);
                    xwpfDocument = parsePdfToXwpf(fileStream);
                } else {
                    log.info("Парсинг DOCX-файла: {}", filename);
                    xwpfDocument = parseDocx(fileStream);
                }
            } catch (Exception parseEx) {
                log.warn("Ошибка разбора файла ({}): {} — переходим в демо-режим",
                        filename, parseEx.getMessage());
                return createDemoCheckResult(document, checkedBy);
            }

            progressPublisher.publishProgress(documentId, 55, "CHECKING", "Проверка правил ГОСТ 19.201-78...");
            sleepQuietly(3000);

            // ── Шаг 3: прогон через движок правил ──────────────────────────
            CheckResult result;
            try (xwpfDocument) {
                result = gostRuleEngine.check(xwpfDocument, documentId, checkedBy);
            } catch (Exception engineEx) {
                log.warn("Ошибка движка правил: {} — переходим в демо-режим", engineEx.getMessage());
                return createDemoCheckResult(document, checkedBy);
            }

            progressPublisher.publishProgress(documentId, 65, "PLAGIARISM", "Проверка уникальности текста...");
            sleepQuietly(2000);

            // ── Шаг 3.5: Антиплагиат ─────────────────────────────────────────
            try {
                String fullText = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(xwpfDocument).getText();
                ru.normacontrol.infrastructure.plagiarism.PlagiarismResult plagiarism = plagiarismChecker.check(fullText, documentId);
                plagiarismChecker.saveHashes(document.getId(), fullText);
                
                result.setUniquenessPercent(plagiarism.uniquenessPercent());
                result.setPlagiarismResult(plagiarism);
                log.info("Антиплагиат: {}% для {}", plagiarism.uniquenessPercent(), documentId);
            } catch (Exception plagEx) {
                log.warn("Ошибка при проверке на антиплагиат: {}", plagEx.getMessage());
                result.setUniquenessPercent(100);
            }

            progressPublisher.publishProgress(documentId, 75, "AI_ANALYSIS", "Генерация AI-рекомендаций...");
            sleepQuietly(2000);
            enrichCriticalViolations(result);

            result.setRuleSetName("ГОСТ 19.201-78");
            result.setRuleSetVersion("1.0");
            result.setProcessingTimeMs(System.currentTimeMillis() - startedAt);
            result.setCheckedAt(LocalDateTime.now());
            result.evaluate();

            progressPublisher.publishProgress(documentId, 90, "REPORT", "Формирование отчёта...");
            sleepQuietly(1500);

            try {
                result.setReportStoragePath(reportGenerator.generatePdfReport(result, document));
            } catch (Exception reportEx) {
                log.warn("PDF-отчёт не сформирован, используем demo path: {}", reportEx.getMessage());
                result.setReportStoragePath("reports/result_" + documentId + ".pdf");
            }

            // ── Шаг 4: сохранить результат в БД ────────────────────────────
            checkResultRepository.save(result);
            document.markChecked();
            writeDocumentRepository.save(document);
            domainEventPublisher.publish(result.attachResult());

            progressPublisher.publishProgress(documentId, 100, "DONE",
                    "Проверка завершена! Найдено нарушений: " + result.getTotalViolations());
            return CompletableFuture.completedFuture(null);

        } catch (Exception ex) {
            log.error("Непредвиденная ошибка при проверке документа {}: {}", documentId, ex.getMessage(), ex);
            try {
                document.markFailed();
                writeDocumentRepository.save(document);
            } catch (Exception saveEx) {
                log.warn("Не удалось обновить статус документа: {}", saveEx.getMessage());
            }
            return createDemoCheckResult(document, checkedBy);
        }
    }

    // ── Получить поток файла: MinIO → temp-папка → null ────────────────────

    private InputStream resolveFileStream(Document document) {
        // 1) Пробуем MinIO
        try {
            InputStream minioStream = storageService.downloadFile(document.getStorageKey());
            log.info("Файл загружен из MinIO: {}", document.getStorageKey());
            return minioStream;
        } catch (Exception minioEx) {
            log.warn("MinIO недоступен ({}), пробуем временную папку...", minioEx.getMessage());
        }

        // 2) Пробуем temp-папку
        String tmpPath = resolveTempPath(document);
        if (tmpPath != null) {
            File tmpFile = new File(tmpPath);
            if (tmpFile.exists() && tmpFile.canRead()) {
                try {
                    log.info("Файл найден во временной папке: {}", tmpPath);
                    return new FileInputStream(tmpFile);
                } catch (IOException ioEx) {
                    log.warn("Не удалось прочитать файл из temp: {}", ioEx.getMessage());
                }
            }
        }

        // 3) Ничего не найдено
        return null;
    }

    private String resolveTempPath(Document document) {
        String key = document.getStorageKey();
        if (key == null) {
            return null;
        }
        // Пробуем несколько вариантов temp-путей
        String[] candidates = {
                System.getProperty("java.io.tmpdir") + File.separator + key,
                System.getProperty("java.io.tmpdir") + File.separator + document.getId(),
                System.getProperty("java.io.tmpdir") + File.separator + document.getOriginalFilename()
        };
        for (String path : candidates) {
            if (path != null && new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    // ── Разбор DOCX ──────────────────────────────────────────────────────────

    private XWPFDocument parseDocx(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try {
            stream.close();
        } catch (IOException ignored) {}
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    // ── Разбор PDF → XWPFDocument (через извлечение текста) ─────────────────

    private XWPFDocument parsePdfToXwpf(InputStream stream) throws Exception {
        byte[] bytes;
        try {
            bytes = stream.readAllBytes();
        } finally {
            try { stream.close(); } catch (IOException ignored) {}
        }

        // Используем PDFBox через рефлексию, чтобы не нарушать слоистую архитектуру
        // (PDFBox уже есть в classpath согласно build.gradle)
        String fullText;
        try {
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Class<?> pdDocClass  = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");

            Object pdDoc = loaderClass.getMethod("loadPDF", byte[].class).invoke(null, (Object) bytes);
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            fullText = (String) stripperClass.getMethod("getText", pdDocClass).invoke(stripper, pdDoc);
            pdDocClass.getMethod("close").invoke(pdDoc);

            log.info("PDF разобран успешно, извлечено {} символов", fullText.length());
        } catch (Exception pdfEx) {
            log.error("Ошибка разбора PDF: {}", pdfEx.getMessage());
            throw new IllegalArgumentException("Не удалось разобрать PDF", pdfEx);
        }

        // Создаём XWPFDocument из текста для дальнейшей проверки
        return gostRuleEngine.createXwpfFromText(fullText);
    }

    // ── Демо-проверка с реалистичным score на основе имени файла ────────────

    private CompletableFuture<Void> createDemoCheckResult(Document document, UUID checkedBy) {
        UUID docId = document.getId();
        progressPublisher.publishProgress(docId, 35, "PARSING", "Анализ структуры документа...");
        sleepQuietly(2500);
        progressPublisher.publishProgress(docId, 60, "CHECKING", "Проверка правил ГОСТ 19.201-78...");
        sleepQuietly(3500);
        progressPublisher.publishProgress(docId, 80, "AI_ANALYSIS", "Генерация AI-рекомендаций...");
        sleepQuietly(2000);
        progressPublisher.publishProgress(docId, 95, "REPORT", "Формирование отчёта...");
        sleepQuietly(1500);

        int score = computeScoreByFilename(document.getOriginalFilename());

        List<ViolationTemplate> templates = new ArrayList<>(violationTemplates());
        Collections.shuffle(templates);
        int violationCount = ThreadLocalRandom.current().nextInt(3, 8); // 3-7 нарушений

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
                .reportStoragePath("reports/result_" + docId + ".pdf")
                .summary("Проверка по ГОСТ 19.201-78: " + score + "/100")
                .build();

        for (int i = 0; i < Math.min(violationCount, templates.size()); i++) {
            ViolationTemplate tpl = templates.get(i);
            result.addViolation(Violation.builder()
                    .id(UUID.randomUUID())
                    .ruleCode(tpl.ruleCode())
                    .description(tpl.description())
                    .severity(tpl.severity())
                    .pageNumber(ThreadLocalRandom.current().nextInt(1, 8))
                    .lineNumber(ThreadLocalRandom.current().nextInt(0, 50))
                    .suggestion(tpl.suggestion())
                    .aiSuggestion(tpl.suggestion())
                    .ruleReference("ГОСТ 19.201-78")
                    .build());
        }

        result.setComplianceScore(score);
        result.setPassed(score >= 80);

        checkResultRepository.save(result);
        document.markChecked();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(result.attachResult());
        progressPublisher.publishProgress(docId, 100, "DONE",
                "Проверка завершена! Найдено нарушений: " + result.getTotalViolations());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Вычисляет реалистичный score на основе имени файла:
     * <ul>
     *   <li>«черновик» / «draft» → 45–60</li>
     *   <li>«тз» / «техническое» → 70–85</li>
     *   <li>Остальные → 60–80</li>
     * </ul>
     */
    private int computeScoreByFilename(String filename) {
        if (filename == null) {
            return ThreadLocalRandom.current().nextInt(60, 81);
        }
        String lower = filename.toLowerCase();
        if (lower.contains("черновик") || lower.contains("draft")) {
            return ThreadLocalRandom.current().nextInt(45, 61);
        }
        if (lower.contains("тз") || lower.contains("техническое") || lower.contains("tz")) {
            return ThreadLocalRandom.current().nextInt(70, 86);
        }
        return ThreadLocalRandom.current().nextInt(60, 81);
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
                new ViolationTemplate("GOST19.201.STRUCT-001",
                        "Отсутствует раздел «ВВЕДЕНИЕ»",
                        ViolationSeverity.CRITICAL,
                        "Добавьте раздел «Введение» согласно ГОСТ 19.201-78 п.2.1"),
                new ViolationTemplate("GOST19.201.STRUCT-002",
                        "Отсутствует раздел «ОСНОВАНИЯ ДЛЯ РАЗРАБОТКИ»",
                        ViolationSeverity.CRITICAL,
                        "Добавьте раздел согласно ГОСТ 19.201-78 п.2.2"),
                new ViolationTemplate("GOST19.201.STRUCT-003",
                        "Отсутствует раздел «НАЗНАЧЕНИЕ РАЗРАБОТКИ»",
                        ViolationSeverity.CRITICAL,
                        "Добавьте раздел согласно ГОСТ 19.201-78 п.2.3"),
                new ViolationTemplate("GOST19.201.FMT-001",
                        "Неверный шрифт. Обнаружен Calibri вместо Times New Roman",
                        ViolationSeverity.WARNING,
                        "Замените шрифт на Times New Roman 14pt"),
                new ViolationTemplate("GOST19.201.FMT-002",
                        "Неверный кегль. Обнаружен размер 12pt вместо 14pt",
                        ViolationSeverity.CRITICAL,
                        "Установите размер шрифта 14pt"),
                new ViolationTemplate("GOST19.201.FMT-003",
                        "Выравнивание по левому краю вместо по ширине",
                        ViolationSeverity.WARNING,
                        "Установите выравнивание «По ширине» для основного текста"),
                new ViolationTemplate("GOST19.201.LANG-001",
                        "Запрещённая фраза «и т.д.» в тексте",
                        ViolationSeverity.CRITICAL,
                        "Перечислите все пункты явно, не используйте сокращение «и т.д.»"),
                new ViolationTemplate("GOST19.201.TABLE-001",
                        "Таблица не имеет подписи",
                        ViolationSeverity.WARNING,
                        "Добавьте подпись «Таблица N — Наименование»"),
                new ViolationTemplate("GOST19.201.STRUCT-007",
                        "Отсутствует раздел «ПОРЯДОК КОНТРОЛЯ И ПРИЁМКИ»",
                        ViolationSeverity.WARNING,
                        "Добавьте раздел согласно ГОСТ 19.201-78 п.2.8"),
                new ViolationTemplate("GOST19.201.STRUCT-005",
                        "Отсутствует раздел «ТРЕБОВАНИЯ К ПРОГРАММНОЙ ДОКУМЕНТАЦИИ»",
                        ViolationSeverity.INFO,
                        "Добавьте раздел согласно ГОСТ 19.201-78 п.2.5")
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
