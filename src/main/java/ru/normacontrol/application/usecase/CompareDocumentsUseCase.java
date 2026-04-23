package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import ru.normacontrol.application.dto.DocumentComparisonDto;
import ru.normacontrol.application.dto.ViolationDto;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.DocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompareDocumentsUseCase {

    private final DocumentRepository documentRepository;
    private final MinioStorageService storageService;
    private final GostRuleEngine gostRuleEngine;

    public DocumentComparisonDto compare(UUID docV1Id, UUID docV2Id, UUID userId) {
        log.info("Начало сравнения документов: {} и {}", docV1Id, docV2Id);

        Document docV1 = documentRepository.findById(docV1Id)
                .orElseThrow(() -> new IllegalArgumentException("Документ V1 не найден: " + docV1Id));
        Document docV2 = documentRepository.findById(docV2Id)
                .orElseThrow(() -> new IllegalArgumentException("Документ V2 не найден: " + docV2Id));

        CompletableFuture<CheckResult> futureV1 = CompletableFuture.supplyAsync(() -> checkDocument(docV1, userId));
        CompletableFuture<CheckResult> futureV2 = CompletableFuture.supplyAsync(() -> checkDocument(docV2, userId));

        CompletableFuture.allOf(futureV1, futureV2).join();

        CheckResult resultV1 = futureV1.join();
        CheckResult resultV2 = futureV2.join();

        Map<String, Violation> v1Map = resultV1.getViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getRuleCode() + ":" + v.getPageNumber() + ":" + v.getLineNumber(), 
                        v -> v, 
                        (vOld, vNew) -> vOld));
                        
        Map<String, Violation> v2Map = resultV2.getViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getRuleCode() + ":" + v.getPageNumber() + ":" + v.getLineNumber(), 
                        v -> v, 
                        (vOld, vNew) -> vOld));

        List<ViolationDto> fixedViolations = resultV1.getViolations().stream()
                .filter(v -> !v2Map.containsKey(v.getRuleCode() + ":" + v.getPageNumber() + ":" + v.getLineNumber()))
                .map(ViolationDto::from)
                .collect(Collectors.toList());

        List<ViolationDto> newViolations = resultV2.getViolations().stream()
                .filter(v -> !v1Map.containsKey(v.getRuleCode() + ":" + v.getPageNumber() + ":" + v.getLineNumber()))
                .map(ViolationDto::from)
                .collect(Collectors.toList());

        List<ViolationDto> persistentViolations = resultV2.getViolations().stream()
                .filter(v -> v1Map.containsKey(v.getRuleCode() + ":" + v.getPageNumber() + ":" + v.getLineNumber()))
                .map(ViolationDto::from)
                .collect(Collectors.toList());

        int scoreV1 = Math.max(0, 100 - (resultV1.getTotalViolations() * 5));
        int scoreV2 = Math.max(0, 100 - (resultV2.getTotalViolations() * 5));
        int scoreImprovement = scoreV2 - scoreV1;

        String summary = String.format("Исправлено %d нарушений, добавилось %d. Балл: %d→%d (%+d)",
                fixedViolations.size(), newViolations.size(), scoreV1, scoreV2, scoreImprovement);

        return DocumentComparisonDto.builder()
                .docV1Id(docV1Id)
                .docV2Id(docV2Id)
                .scoreV1(scoreV1)
                .scoreV2(scoreV2)
                .scoreImprovement(scoreImprovement)
                .fixedViolations(fixedViolations)
                .newViolations(newViolations)
                .persistentViolations(persistentViolations)
                .summary(summary)
                .build();
    }

    private CheckResult checkDocument(Document document, UUID userId) {
        try (InputStream fileStream = storageService.downloadFile(document.getStorageKey());
             XWPFDocument xwpfDocument = new XWPFDocument(fileStream)) {
             
            return gostRuleEngine.check(xwpfDocument, document.getId(), userId);
        } catch (Exception e) {
            log.error("Ошибка при проверке документа {} для сравнения", document.getId(), e);
            throw new RuntimeException("Не удалось проверить документ " + document.getId(), e);
        }
    }
}
