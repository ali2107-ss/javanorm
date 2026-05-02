package ru.normacontrol.application.usecase;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.normacontrol.application.dto.DocumentComparisonDto;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.domain.service.GostRuleEngine;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareDocumentsUseCaseTest {

    @Mock
    private GostRuleEngine gostRuleEngine;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private ReadDocumentRepository documentRepository;

    private CompareDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CompareDocumentsUseCase(documentRepository, storageService, gostRuleEngine);
    }

    @Test
    void testCompare_v2Better_scoreImproved() throws Exception {
        UUID userId = UUID.randomUUID();
        Document v1 = document(UUID.randomUUID(), userId, "v1.docx");
        Document v2 = document(UUID.randomUUID(), userId, "v2.docx");
        mockDocsAndStreams(v1, v2);

        when(gostRuleEngine.check(any(), eq(v1.getId()), eq(userId))).thenReturn(result(70, violation("A")));
        when(gostRuleEngine.check(any(), eq(v2.getId()), eq(userId))).thenReturn(result(90));

        DocumentComparisonDto dto = useCase.compare(v1.getId(), v2.getId(), userId);

        assertTrue(dto.scoreImprovement() > 0);
        assertFalse(dto.fixedViolations().isEmpty());
    }

    @Test
    void testCompare_v2Worse_scoreDropped() throws Exception {
        UUID userId = UUID.randomUUID();
        Document v1 = document(UUID.randomUUID(), userId, "v1.docx");
        Document v2 = document(UUID.randomUUID(), userId, "v2.docx");
        mockDocsAndStreams(v1, v2);

        when(gostRuleEngine.check(any(), eq(v1.getId()), eq(userId))).thenReturn(result(95));
        when(gostRuleEngine.check(any(), eq(v2.getId()), eq(userId))).thenReturn(result(70, violation("NEW")));

        DocumentComparisonDto dto = useCase.compare(v1.getId(), v2.getId(), userId);

        assertTrue(dto.scoreImprovement() < 0);
        assertFalse(dto.newViolations().isEmpty());
    }

    @Test
    void testCompare_identical_noDiff() throws Exception {
        UUID userId = UUID.randomUUID();
        Document v1 = document(UUID.randomUUID(), userId, "v1.docx");
        Document v2 = document(UUID.randomUUID(), userId, "v2.docx");
        mockDocsAndStreams(v1, v2);

        CheckResult result1 = result(88, violation("SAME"));
        CheckResult result2 = result(88, violation("SAME"));

        when(gostRuleEngine.check(any(), eq(v1.getId()), eq(userId))).thenReturn(result1);
        when(gostRuleEngine.check(any(), eq(v2.getId()), eq(userId))).thenReturn(result2);

        DocumentComparisonDto dto = useCase.compare(v1.getId(), v2.getId(), userId);

        assertEquals(0, dto.scoreImprovement());
        assertTrue(dto.fixedViolations().isEmpty());
        assertTrue(dto.newViolations().isEmpty());
    }

    @Test
    void testCompare_notOwner_throwsSecurityException() {
        UUID userId = UUID.randomUUID();
        Document v1 = document(UUID.randomUUID(), UUID.randomUUID(), "v1.docx");
        Document v2 = document(UUID.randomUUID(), userId, "v2.docx");

        when(documentRepository.findById(v1.getId())).thenReturn(Optional.of(v1));
        when(documentRepository.findById(v2.getId())).thenReturn(Optional.of(v2));

        assertThrows(SecurityException.class, () -> useCase.compare(v1.getId(), v2.getId(), userId));
    }

    private void mockDocsAndStreams(Document v1, Document v2) throws Exception {
        byte[] bytes = validDocxBytes();
        when(documentRepository.findById(v1.getId())).thenReturn(Optional.of(v1));
        when(documentRepository.findById(v2.getId())).thenReturn(Optional.of(v2));
        when(storageService.downloadFile(v1.getStorageKey())).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        when(storageService.downloadFile(v2.getStorageKey())).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
    }

    private static Document document(UUID id, UUID ownerId, String name) {
        return Document.builder()
                .id(id)
                .ownerId(ownerId)
                .originalFilename(name)
                .storageKey("storage/" + name)
                .status(DocumentStatus.CHECKED)
                .build();
    }

    private static CheckResult result(int score, Violation... violations) {
        CheckResult result = CheckResult.builder()
                .complianceScore(score)
                .violations(new ArrayList<>())
                .build();
        for (Violation violation : violations) {
            result.addViolation(violation);
        }
        result.evaluate();
        return result;
    }

    private static Violation violation(String code) {
        return Violation.builder()
                .id(UUID.randomUUID())
                .ruleCode(code)
                .description("Violation " + code)
                .severity(ViolationSeverity.WARNING)
                .pageNumber(1)
                .lineNumber(1)
                .build();
    }

    private static byte[] validDocxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Test");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
