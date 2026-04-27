package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.DocumentComparisonDto;
import ru.normacontrol.application.dto.ViolationDto;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Use case for comparing two document revisions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompareDocumentsUseCase {

    private final ReadDocumentRepository readDocumentRepository;
    private final MinioStorageService storageService;
    private final GostRuleEngine gostRuleEngine;

    /**
     * Compare two document revisions for the same user.
     *
     * @param docV1Id first document identifier
     * @param docV2Id second document identifier
     * @param userId owner identifier
     * @return comparison DTO
     */
    @Transactional(readOnly = true)
    public DocumentComparisonDto compare(UUID docV1Id, UUID docV2Id, UUID userId) {
        Document docV1 = readDocumentRepository.findById(docV1Id)
                .orElseThrow(() -> new IllegalArgumentException("Документ V1 не найден: " + docV1Id));
        Document docV2 = readDocumentRepository.findById(docV2Id)
                .orElseThrow(() -> new IllegalArgumentException("Документ V2 не найден: " + docV2Id));

        CompletableFuture<CheckResult> futureV1 = CompletableFuture.supplyAsync(() -> checkDocument(docV1, userId));
        CompletableFuture<CheckResult> futureV2 = CompletableFuture.supplyAsync(() -> checkDocument(docV2, userId));
        CompletableFuture.allOf(futureV1, futureV2).join();

        CheckResult resultV1 = futureV1.join();
        CheckResult resultV2 = futureV2.join();

        Map<String, Violation> v1Map = resultV1.getViolations().stream()
                .collect(Collectors.toMap(this::violationKey, violation -> violation, (left, right) -> left));
        Map<String, Violation> v2Map = resultV2.getViolations().stream()
                .collect(Collectors.toMap(this::violationKey, violation -> violation, (left, right) -> left));

        List<ViolationDto> fixedViolations = resultV1.getViolations().stream()
                .filter(violation -> !v2Map.containsKey(violationKey(violation)))
                .map(ViolationDto::from)
                .toList();
        List<ViolationDto> newViolations = resultV2.getViolations().stream()
                .filter(violation -> !v1Map.containsKey(violationKey(violation)))
                .map(ViolationDto::from)
                .toList();
        List<ViolationDto> persistentViolations = resultV2.getViolations().stream()
                .filter(violation -> v1Map.containsKey(violationKey(violation)))
                .map(ViolationDto::from)
                .toList();

        int scoreV1 = resultV1.calculateScore();
        int scoreV2 = resultV2.calculateScore();

        return DocumentComparisonDto.builder()
                .docV1Id(docV1Id)
                .docV2Id(docV2Id)
                .scoreV1(scoreV1)
                .scoreV2(scoreV2)
                .scoreImprovement(scoreV2 - scoreV1)
                .fixedViolations(fixedViolations)
                .newViolations(newViolations)
                .persistentViolations(persistentViolations)
                .summary("Исправлено " + fixedViolations.size() + ", новых " + newViolations.size())
                .build();
    }

    private CheckResult checkDocument(Document document, UUID userId) {
        try (InputStream fileStream = storageService.downloadFile(document.getStorageKey());
             XWPFDocument xwpfDocument = new XWPFDocument(fileStream)) {
            return gostRuleEngine.check(xwpfDocument, document.getId(), userId);
        } catch (Exception ex) {
            throw new RuntimeException("Не удалось проверить документ " + document.getId(), ex);
        }
    }

    private String violationKey(Violation violation) {
        return violation.getRuleCode() + ":" + violation.getPageNumber() + ":" + violation.getLineNumber();
    }
}
