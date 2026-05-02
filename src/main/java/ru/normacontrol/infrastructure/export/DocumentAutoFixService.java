package ru.normacontrol.infrastructure.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис авто-исправления документов DOCX.
 *
 * <p>Загружает оригинальный DOCX из MinIO, применяет исправления
 * (шрифт, выравнивание, запрещённые фразы, отсутствующие разделы)
 * и сохраняет исправленный файл обратно в MinIO.</p>
 *
 * <h3>Применяемые исправления:</h3>
 * <ul>
 *   <li><b>FMT-001</b> — Неправильный шрифт → Times New Roman 14pt</li>
 *   <li><b>FMT-002</b> — Неправильное выравнивание → BOTH (по ширине)</li>
 *   <li><b>LANG-*</b>  — Запрещённые фразы → подсвечены жёлтым (highlight)</li>
 *   <li><b>STRUCT-*</b>— Отсутствующий раздел → placeholder красным текстом</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAutoFixService {

    private static final String FIXED_PREFIX = "fixed/";
    private static final String PLACEHOLDER_COLOR = "FF0000";    // красный
    private static final String HIGHLIGHT_COLOR   = "FFFF00";    // жёлтый

    private static final String TARGET_FONT  = "Times New Roman";
    private static final int    TARGET_SIZE  = 28;               // half-points (14pt × 2)

    /** Запрещённые слова-маркеры (из LANG-стратегии). */
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "очевидно", "конечно", "несомненно",
            "является", "осуществляет", "производит", "имеет место"
    );

    /** Разделы ГОСТ 19.201-78, обязательные в документе. */
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "Введение",
            "Основания для разработки",
            "Назначение разработки",
            "Требования к программе",
            "Требования к программной документации"
    );

    private final ReadDocumentRepository readDocumentRepository;
    private final CheckResultRepository  checkResultRepository;
    private final MinioStorageService    storageService;

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Автоматически исправить документ и сохранить результат в MinIO.
     *
     * @param documentId идентификатор документа
     * @param userId     пользователь, инициировавший исправление
     * @return исправленный DOCX в виде массива байт
     */
    public byte[] autoFix(UUID documentId, UUID userId) {
        log.info("Запуск авто-исправления документа {} для пользователя {}", documentId, userId);

        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Документ не найден: " + documentId));

        CheckResult checkResult = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Результат проверки не найден для документа: " + documentId));

        byte[] originalBytes = storageService.downloadBytes(document.getStorageKey());

        try (InputStream in  = new ByteArrayInputStream(originalBytes);
             XWPFDocument doc = new XWPFDocument(in)) {

            AtomicInteger fixCount = new AtomicInteger(0);
            List<Violation> violations = checkResult.getViolations();

            applyFontAndAlignment(doc, violations, fixCount);
            highlightForbiddenPhrases(doc, violations, fixCount);
            addMissingSectonPlaceholders(doc, violations, fixCount);

            byte[] fixedBytes = toBytes(doc);

            String originalName = document.getOriginalFilename();
            String fixedKey     = FIXED_PREFIX + documentId + "/fixed_" + originalName;
            storageService.upload(fixedKey, fixedBytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            log.info("Авто-исправление завершено: {} исправлений, ключ={}", fixCount.get(), fixedKey);
            return fixedBytes;

        } catch (Exception ex) {
            log.error("Ошибка авто-исправления документа {}: {}", documentId, ex.getMessage(), ex);
            throw new RuntimeException("Ошибка авто-исправления: " + ex.getMessage(), ex);
        }
    }

    /**
     * Построить путь MinIO для исправленного файла.
     *
     * @param documentId идентификатор документа
     * @param originalName оригинальное имя файла
     * @return ключ объекта в MinIO
     */
    public String buildFixedKey(UUID documentId, String originalName) {
        return FIXED_PREFIX + documentId + "/fixed_" + originalName;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Внутренние методы

    /**
     * Применить правильный шрифт (Times New Roman 14pt) и выравнивание по ширине.
     * Срабатывает для нарушений с префиксами FMT-001 и FMT-002.
     */
    private void applyFontAndAlignment(XWPFDocument doc,
                                       List<Violation> violations,
                                       AtomicInteger fixCount) {
        boolean hasFontViolation = violations.stream()
                .anyMatch(v -> v.getRuleCode() != null && v.getRuleCode().startsWith("FMT-001"));
        boolean hasAlignViolation = violations.stream()
                .anyMatch(v -> v.getRuleCode() != null && v.getRuleCode().startsWith("FMT-002"));

        if (!hasFontViolation && !hasAlignViolation) return;

        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            // Выравнивание
            if (hasAlignViolation
                    && paragraph.getAlignment() != ParagraphAlignment.BOTH
                    && !isHeading(paragraph)) {
                paragraph.setAlignment(ParagraphAlignment.BOTH);
                fixCount.incrementAndGet();
            }

            // Шрифт и размер
            if (hasFontViolation) {
                for (XWPFRun run : paragraph.getRuns()) {
                    boolean changed = false;
                    if (!TARGET_FONT.equals(run.getFontFamily())) {
                        run.setFontFamily(TARGET_FONT);
                        changed = true;
                    }
                    if (run.getFontSize() != -1 && run.getFontSize() != 14) {
                        run.setFontSize(14);
                        changed = true;
                    }
                    if (changed) fixCount.incrementAndGet();
                }
            }
        }
    }

    /**
     * Подсветить жёлтым запрещённые фразы (нарушения LANG-*).
     */
    private void highlightForbiddenPhrases(XWPFDocument doc,
                                           List<Violation> violations,
                                           AtomicInteger fixCount) {
        boolean hasLangViolation = violations.stream()
                .anyMatch(v -> v.getRuleCode() != null && v.getRuleCode().startsWith("LANG"));
        if (!hasLangViolation) return;

        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            String text = paragraph.getText().toLowerCase();
            for (String phrase : FORBIDDEN_PHRASES) {
                if (text.contains(phrase)) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        if (run.getText(0) != null
                                && run.getText(0).toLowerCase().contains(phrase)) {
                            run.setTextHighlightColor("yellow");
                            fixCount.incrementAndGet();
                        }
                    }
                }
            }
        }
    }

    /**
     * Добавить placeholder для отсутствующих обязательных разделов (STRUCT-*).
     */
    private void addMissingSectonPlaceholders(XWPFDocument doc,
                                              List<Violation> violations,
                                              AtomicInteger fixCount) {
        boolean hasStructViolation = violations.stream()
                .anyMatch(v -> v.getRuleCode() != null && v.getRuleCode().startsWith("STRUCT"));
        if (!hasStructViolation) return;

        // Собрать заголовки, уже присутствующие в документе
        List<String> existingHeaders = doc.getParagraphs().stream()
                .filter(this::isHeading)
                .map(XWPFParagraph::getText)
                .map(String::toLowerCase)
                .toList();

        for (String section : REQUIRED_SECTIONS) {
            boolean found = existingHeaders.stream()
                    .anyMatch(h -> h.contains(section.toLowerCase()));
            if (!found) {
                addRedPlaceholder(doc, section);
                fixCount.incrementAndGet();
                log.debug("Добавлен placeholder для раздела «{}»", section);
            }
        }
    }

    /** Добавить абзац-placeholder красным текстом в конец документа. */
    private void addRedPlaceholder(XWPFDocument doc, String sectionName) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);

        XWPFRun run = para.createRun();
        run.setText("[ДОБАВЬТЕ РАЗДЕЛ: " + sectionName.toUpperCase() + "]");
        run.setBold(true);
        run.setFontFamily(TARGET_FONT);
        run.setFontSize(14);
        run.setColor(PLACEHOLDER_COLOR);
    }

    /** Проверить, является ли абзац заголовком (Heading 1..3). */
    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && (
                style.startsWith("Heading") || style.startsWith("1") ||
                style.equalsIgnoreCase("heading1") ||
                style.equalsIgnoreCase("heading2") ||
                style.equalsIgnoreCase("heading3")
        );
    }

    /** Сериализовать XWPFDocument в байты. */
    private byte[] toBytes(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            return out.toByteArray();
        }
    }
}
