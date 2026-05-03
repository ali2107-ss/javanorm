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
import ru.normacontrol.domain.event.DomainEventPublisher;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.repository.WriteDocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.ai.AiRecommendationService;
import ru.normacontrol.infrastructure.audit.AuditLogged;
import ru.normacontrol.infrastructure.minio.MinioStorageService;
import ru.normacontrol.infrastructure.parser.DocumentParserChain;
import ru.normacontrol.infrastructure.parser.DocumentType;
import ru.normacontrol.infrastructure.parser.ParsedDocument;
import ru.normacontrol.infrastructure.plagiarism.PlagiarismResult;
import ru.normacontrol.infrastructure.report.ReportGenerator;
import ru.normacontrol.infrastructure.websocket.ProgressPublisher;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDocumentUseCase {

    private final ReadDocumentRepository readDocumentRepository;
    private final WriteDocumentRepository writeDocumentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;
    private final GostRuleEngine ruleEngine;
    private final CheckResultMapper checkResultMapper;
    private final AiRecommendationService aiRecommendationService;
    private final ProgressPublisher progressPublisher;
    private final DomainEventPublisher domainEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReportGenerator reportGenerator;
    private final DocumentParserChain parserChain;
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

    private DocumentType detectType(String fileName) {
        if (fileName == null) 
            throw new IllegalArgumentException("Имя файла пустое");
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".docx")) return DocumentType.DOCX;
        if (lower.endsWith(".doc"))  return DocumentType.DOCX;
        if (lower.endsWith(".pdf"))  return DocumentType.PDF;
        if (lower.endsWith(".txt"))  return DocumentType.TXT;
        if (lower.endsWith(".md"))   return DocumentType.MD;
        throw new IllegalArgumentException(
            "Неподдерживаемый формат: " + fileName + 
            ". Поддерживаются: DOCX, PDF, TXT, MD");
    }

    @Async("checkExecutor")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CompletableFuture<CheckResult> executeCheck(UUID documentId, UUID checkedBy) {
      
      long startMs = System.currentTimeMillis();
      Document document = readDocumentRepository.findById(documentId)
        .orElseThrow();
      
      document.setStatus(ru.normacontrol.domain.enums.DocumentStatus.CHECKING);
      writeDocumentRepository.save(document);

      try {
        DocumentType docType = detectType(document.getOriginalFilename());

        progressPublisher.publishProgress(documentId, 15,
          "DOWNLOADING", "Загрузка файла из хранилища...");
        
        InputStream fileStream = storageService
          .download(document.getStorageKey());

        progressPublisher.publishProgress(documentId, 30,
          "PARSING", "Разбор документа " + 
          document.getOriginalFilename() + "...");
        
        ParsedDocument parsed = parserChain.parse(fileStream, docType);

        progressPublisher.publishProgress(documentId, 55,
          "CHECKING", "Проверка по правилам ГОСТ 19.201-78...");
        
        List<Violation> violations;
        if (docType == DocumentType.DOCX) {
          try (InputStream stream2 = storageService.download(document.getStorageKey());
               XWPFDocument xwpf = new XWPFDocument(stream2)) {
            violations = ruleEngine.check(xwpf, documentId, checkedBy).getViolations();
          }
        } else {
          violations = ruleEngine.checkText(
            parsed.fullText(), parsed.sections());
        }

        progressPublisher.publishProgress(documentId, 70,
          "PLAGIARISM", "Проверка уникальности текста...");
        PlagiarismResult plagiarismResult = plagiarismChecker.check(parsed.fullText(), documentId);
        plagiarismChecker.saveHashes(documentId, parsed.fullText());
        addPlagiarismViolationIfNeeded(violations, plagiarismResult);

        progressPublisher.publishProgress(documentId, 82,
          "AI_ANALYSIS", "Генерация рекомендаций...");
        fillRecommendations(violations, parsed.fullText());

        progressPublisher.publishProgress(documentId, 93,
          "REPORT", "Формирование PDF-отчёта...");

        CheckResult result = CheckResult.builder()
          .id(UUID.randomUUID())
          .documentId(documentId)
          .checkedBy(checkedBy)
          .checkedAt(LocalDateTime.now())
          .ruleSetName("ГОСТ 19.201-78")
          .ruleSetVersion("1.0")
          .processingTimeMs(System.currentTimeMillis() - startMs)
          .uniquenessPercent(plagiarismResult.uniquenessPercent())
          .plagiarismResult(plagiarismResult)
          .build();

        violations.forEach(result::addViolation);
        result.evaluate();

        // Генерировать PDF и сохранить
        try {
          byte[] pdfBytes = reportGenerator
            .generatePdfBytes(result, document);
          String reportPath = "reports/" + documentId + "/report.pdf";
          storageService.uploadBytes(reportPath, pdfBytes, 
            "application/pdf");
          result.setReportStoragePath(reportPath);
        } catch (Exception e) {
          log.warn("PDF не сгенерирован: {}", e.getMessage());
        }

        checkResultRepository.save(result);
        document.markChecked();
        writeDocumentRepository.save(document);
        domainEventPublisher.publish(result.attachResult());

        progressPublisher.publishProgress(documentId, 100,
          "DONE", "Проверка завершена! Балл: " + 
          result.getComplianceScore() + "/100");

        log.info("Документ {} проверен. Балл: {}/100, нарушений: {}",
          documentId, result.getComplianceScore(), violations.size());

        return CompletableFuture.completedFuture(result);

      } catch (Exception e) {
        log.error("Ошибка проверки {}: {}", documentId, 
          e.getMessage(), e);
        document.setStatus(ru.normacontrol.domain.enums.DocumentStatus.FAILED);
        writeDocumentRepository.save(document);
        progressPublisher.publishProgress(documentId, 0,
          "FAILED", "Ошибка: " + e.getMessage());
        return CompletableFuture.failedFuture(e);
      }
    }

    @Transactional(readOnly = true)
    @AuditLogged(action = "VIEW_RESULT", resourceType = "CHECK_RESULT")
    public CheckResultResponse getLatestResult(UUID documentId) {
        CheckResult result = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Результат проверки не найден: " + documentId));
        return checkResultMapper.toResponse(result);
    }

    private void fillRecommendations(List<Violation> violations, String fullText) {
        String fragment = fullText == null ? "" : fullText.substring(0, Math.min(fullText.length(), 1200));
        for (Violation violation : violations) {
            try {
                String recommendation = aiRecommendationService
                        .generateRecommendation(violation, fragment)
                        .get(8, TimeUnit.SECONDS);
                violation.setAiSuggestion(recommendation);
            } catch (Exception e) {
                if (violation.getSuggestion() == null || violation.getSuggestion().isBlank()) {
                    violation.setSuggestion("Исправьте нарушение согласно указанному пункту ГОСТ.");
                }
                log.warn("Не удалось сформировать рекомендацию для {}: {}", violation.getRuleCode(), e.getMessage());
            }
        }
    }

    private void addPlagiarismViolationIfNeeded(List<Violation> violations, PlagiarismResult plagiarismResult) {
        if (plagiarismResult == null || plagiarismResult.uniquenessPercent() >= 70) {
            return;
        }
        violations.add(Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode("PLAGIARISM.LOW_UNIQUENESS")
                .description("Уникальность текста " + plagiarismResult.uniquenessPercent()
                        + "%, ниже минимального порога 70%")
                .severity(ViolationSeverity.CRITICAL)
                .pageNumber(0)
                .lineNumber(0)
                .suggestion("Перепишите совпадающие фрагменты своими словами и добавьте ссылки на источники.")
                .ruleReference("Антиплагиат: минимальная уникальность 70%")
                .build());
    }
}
