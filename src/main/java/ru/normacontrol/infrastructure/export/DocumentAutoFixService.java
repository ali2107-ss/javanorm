package ru.normacontrol.infrastructure.export;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.stereotype.Service;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.repository.CheckResultRepository;
import ru.normacontrol.domain.repository.ReadDocumentRepository;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAutoFixService {

    private static final String FIXED_PREFIX = "fixed/";
    private static final String TARGET_FONT = "Times New Roman";
    private static final int BODY_FONT_SIZE = 14;
    private static final int HEADING_FONT_SIZE = 14;

    private static final Map<String, String> PHRASE_REPLACEMENTS = Map.of(
            "и т.д.", "и так далее",
            "и т.п.", "и тому подобное",
            "и пр.", "и прочее",
            "и др.", "и другие"
    );

    private static final Pattern PAST_TENSE_PATTERN = Pattern.compile(
            "\\b(был|была|было|использовал|использовала|разработал|разработала|выполнял|выполняла)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final List<SectionPlan> SECTION_ORDER = List.of(
            new SectionPlan("STRUCT-001", 1, "Введение",
                    "В разделе указываются наименование программы, область применения и краткое назначение разработки.",
                    "ГОСТ 19.201-78 п.2.1"),
            new SectionPlan("STRUCT-002", 1, "Основания для разработки",
                    "Основанием для разработки является задание на проектирование, учебное задание или договор.",
                    "ГОСТ 19.201-78 п.2.2"),
            new SectionPlan("STRUCT-003", 1, "Назначение разработки",
                    "Программа предназначена для автоматизации заявленных функций и обработки данных пользователя.",
                    "ГОСТ 19.201-78 п.2.3"),
            new SectionPlan("STRUCT-004", 1, "Требования к программе",
                    "В разделе приводятся функциональные требования, требования к надежности, условия эксплуатации и совместимости.",
                    "ГОСТ 19.201-78 п.2.4"),
            new SectionPlan("STRUCT-004-A", 2, "Требования к функциональным характеристикам",
                    "Программа должна обеспечивать выполнение основных операций, предусмотренных назначением системы.",
                    "ГОСТ 19.201-78 п.2.4.1"),
            new SectionPlan("STRUCT-004-B", 2, "Требования к надежности",
                    "Программа должна корректно обрабатывать ошибки ввода, сохранять целостность данных и вести журналирование.",
                    "ГОСТ 19.201-78 п.2.4.2"),
            new SectionPlan("STRUCT-004-C", 2, "Условия эксплуатации",
                    "Эксплуатация программы выполняется на рабочем месте пользователя при стандартных условиях работы оборудования.",
                    "ГОСТ 19.201-78 п.2.4.3"),
            new SectionPlan("STRUCT-004-D", 2, "Требования к составу и параметрам технических средств",
                    "Минимальный состав технических средств включает компьютер, устройство ввода и доступ к необходимым ресурсам.",
                    "ГОСТ 19.201-78 п.2.4.4"),
            new SectionPlan("STRUCT-004-E", 2, "Требования к информационной и программной совместимости",
                    "Программа должна использовать совместимые форматы данных и работать в заявленной программной среде.",
                    "ГОСТ 19.201-78 п.2.4.5"),
            new SectionPlan("STRUCT-005", 1, "Требования к программной документации",
                    "Состав программной документации включает техническое задание, описание программы и руководство пользователя.",
                    "ГОСТ 19.201-78 п.2.5"),
            new SectionPlan("STRUCT-006", 1, "Стадии и этапы разработки",
                    "Разработка выполняется по этапам: анализ требований, проектирование, реализация, тестирование и приемка.",
                    "ГОСТ 19.201-78 п.2.7"),
            new SectionPlan("STRUCT-007", 1, "Порядок контроля и приёмки",
                    "Контроль и приемка выполняются путем проверки соответствия программы требованиям технического задания.",
                    "ГОСТ 19.201-78 п.2.8")
    );

    private static final Map<String, SectionPlan> SECTIONS_BY_CODE = SECTION_ORDER.stream()
            .collect(LinkedHashMap::new, (map, section) -> map.put(section.code(), section), LinkedHashMap::putAll);

    private final ReadDocumentRepository readDocumentRepository;
    private final CheckResultRepository checkResultRepository;
    private final MinioStorageService storageService;

    public record AutoFixResult(byte[] bytes, String fixedKey, int fixedCount, List<String> manualActions) {
    }

    public byte[] autoFix(UUID documentId, UUID userId) {
        return autoFixWithResult(documentId, userId).bytes();
    }

    public AutoFixResult autoFixWithResult(UUID documentId, UUID userId) {
        log.info("Starting auto-fix for document {} by user {}", documentId, userId);

        Document document = readDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
        CheckResult checkResult = checkResultRepository.findLatestByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Check result not found for document: " + documentId));

        byte[] originalBytes = storageService.downloadBytes(document.getStorageKey());

        try (XWPFDocument doc = openOrBuildDocx(document, originalBytes)) {
            AtomicInteger fixCount = new AtomicInteger(0);
            List<String> manualActions = new ArrayList<>();
            List<Violation> violations = checkResult.getViolations() == null ? List.of() : checkResult.getViolations();

            applyFontAndAlignment(doc, violations, fixCount);
            replaceForbiddenPhrases(doc, violations, fixCount);
            addMissingTableCaption(doc, violations, fixCount, manualActions);
            addMissingSections(doc, violations, fixCount, manualActions);
            collectManualActions(violations, manualActions);

            byte[] fixedBytes = toBytes(doc);
            String fixedKey = buildFixedKey(documentId, document.getOriginalFilename());
            storageService.upload(fixedKey, fixedBytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            log.info("Auto-fix completed: fixedCount={}, manualActions={}, key={}",
                    fixCount.get(), manualActions.size(), fixedKey);
            return new AutoFixResult(fixedBytes, fixedKey, fixCount.get(), List.copyOf(manualActions));
        } catch (Exception ex) {
            log.error("Auto-fix failed for document {}: {}", documentId, ex.getMessage(), ex);
            throw new RuntimeException("Ошибка авто-исправления: " + ex.getMessage(), ex);
        }
    }

    public String buildFixedKey(UUID documentId, String originalName) {
        String safeName = originalName != null && !originalName.isBlank() ? originalName : "document.docx";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            safeName = safeName.substring(0, dot);
        }
        return FIXED_PREFIX + documentId + "/fixed_" + safeName + ".docx";
    }

    private XWPFDocument openOrBuildDocx(Document document, byte[] originalBytes) {
        if (looksLikeDocx(originalBytes)) {
            try {
                return new XWPFDocument(new ByteArrayInputStream(originalBytes));
            } catch (Exception ex) {
                log.warn("Source file looks like DOCX but cannot be opened: {}", ex.getMessage());
            }
        }

        XWPFDocument doc = new XWPFDocument();
        String text = extractText(document, originalBytes);
        if (text.isBlank()) {
            addBodyParagraph(doc, "Текст исходного файла не извлечен. Проверьте исходный формат документа вручную.");
            return doc;
        }

        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                addBodyParagraph(doc, trimmed);
            }
        }
        return doc;
    }

    private boolean looksLikeDocx(byte[] bytes) {
        return bytes != null && bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
    }

    private String extractText(Document document, byte[] bytes) {
        String name = document.getOriginalFilename() != null
                ? document.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        if (name.endsWith(".pdf") || "PDF".equalsIgnoreCase(document.getContentType())) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(pdf);
            } catch (Exception ex) {
                log.warn("Could not extract text from PDF for auto-fix: {}", ex.getMessage());
            }
        }

        String text = new String(bytes, StandardCharsets.UTF_8)
                .replace("\u0000", "")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .trim();
        return text.length() > 200_000 ? text.substring(0, 200_000) : text;
    }

    private void applyFontAndAlignment(XWPFDocument doc, List<Violation> violations, AtomicInteger fixCount) {
        boolean hasFontViolation = hasAnyCode(violations, "FORMAT.WRONG_FONT", "FORMAT.WRONG_FONT_SIZE", "FMT-001", "FMT-002");
        boolean hasAlignViolation = hasAnyCode(violations, "FORMAT.WRONG_ALIGNMENT", "FMT-003");
        if (!hasFontViolation && !hasAlignViolation) {
            return;
        }

        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            if (hasAlignViolation && paragraph.getAlignment() != ParagraphAlignment.BOTH) {
                paragraph.setAlignment(ParagraphAlignment.BOTH);
                fixCount.incrementAndGet();
            }
            if (hasFontViolation) {
                ensureRuns(paragraph);
                for (XWPFRun run : paragraph.getRuns()) {
                    boolean changed = applyRunStyle(run, isHeading(paragraph), null);
                    if (changed) {
                        fixCount.incrementAndGet();
                    }
                }
            }
        }
    }

    private void replaceForbiddenPhrases(XWPFDocument doc, List<Violation> violations, AtomicInteger fixCount) {
        if (!hasAnyCode(violations, "LANGUAGE.FORBIDDEN_PHRASE", "LANG")) {
            return;
        }

        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.getText(0);
                if (text == null || text.isBlank()) {
                    continue;
                }
                String replaced = replaceIgnoreCase(text, PHRASE_REPLACEMENTS);
                if (!text.equals(replaced)) {
                    run.setText(replaced, 0);
                    fixCount.incrementAndGet();
                }
            }
        }
    }

    private void addMissingTableCaption(XWPFDocument doc,
                                        List<Violation> violations,
                                        AtomicInteger fixCount,
                                        List<String> manualActions) {
        if (!hasAnyCode(violations, "TABLE.MISSING_CAPTION") || doc.getTables().isEmpty()) {
            return;
        }

        XWPFTable table = doc.getTables().get(0);
        try (XmlCursor cursor = table.getCTTbl().newCursor()) {
            XWPFParagraph caption = doc.insertNewParagraph(cursor);
            caption.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = caption.createRun();
            run.setText("Таблица 1 - Название таблицы");
            run.setFontFamily(TARGET_FONT);
            run.setFontSize(BODY_FONT_SIZE);
            run.setBold(true);
            fixCount.incrementAndGet();
            manualActions.add("Проверьте подпись таблицы: замените \"Название таблицы\" на точное название.");
        } catch (Exception ex) {
            manualActions.add("Добавьте подпись перед таблицей вручную: \"Таблица 1 - Название таблицы\".");
        }
    }

    private void addMissingSections(XWPFDocument doc,
                                    List<Violation> violations,
                                    AtomicInteger fixCount,
                                    List<String> manualActions) {
        List<SectionPlan> missing = new ArrayList<>(violations.stream()
                .map(Violation::getRuleCode)
                .map(SECTIONS_BY_CODE::get)
                .filter(section -> section != null && !documentContains(doc, section.title()))
                .distinct()
                .toList());

        boolean requirementsSectionWillExist = missing.stream().anyMatch(section -> section.code().equals("STRUCT-004"))
                || documentContains(doc, "Требования к программе")
                || documentContains(doc, "Требования к программному изделию");
        if (requirementsSectionWillExist) {
            SECTION_ORDER.stream()
                    .filter(section -> section.code().startsWith("STRUCT-004-"))
                    .filter(section -> !documentContains(doc, section.title()))
                    .filter(section -> missing.stream().noneMatch(existing -> existing.code().equals(section.code())))
                    .forEach(missing::add);
        }

        missing.sort(Comparator.comparingInt(SECTION_ORDER::indexOf));

        for (SectionPlan section : missing) {
            addSectionAtBestPosition(doc, section);
            fixCount.incrementAndGet();
            manualActions.add("Заполните раздел \"" + section.title() + "\" фактическими данными вашего проекта.");
        }
    }

    private void addSectionAtBestPosition(XWPFDocument doc, SectionPlan section) {
        XWPFParagraph anchor = findPreviousSectionParagraph(doc, section);
        if (anchor == null) {
            addSectionAtEnd(doc, section);
            return;
        }

        try (XmlCursor cursor = anchor.getCTP().newCursor()) {
            cursor.toEndToken();
            XWPFParagraph heading = doc.insertNewParagraph(cursor);
            styleSectionHeading(heading, section);
        } catch (Exception ex) {
            log.warn("Could not insert section near anchor, appending instead: {}", ex.getMessage());
            addSectionAtEnd(doc, section);
        }
    }

    private XWPFParagraph findPreviousSectionParagraph(XWPFDocument doc, SectionPlan section) {
        int index = SECTION_ORDER.indexOf(section);
        for (int i = index - 1; i >= 0; i--) {
            SectionPlan previous = SECTION_ORDER.get(i);
            XWPFParagraph found = findParagraphContaining(doc, previous.title());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private XWPFParagraph findParagraphContaining(XWPFDocument doc, String text) {
        String normalized = normalize(text);
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            if (normalize(paragraph.getText()).contains(normalized)) {
                return paragraph;
            }
        }
        return null;
    }

    private void addSectionAtEnd(XWPFDocument doc, SectionPlan section) {
        XWPFParagraph heading = doc.createParagraph();
        styleSectionHeading(heading, section);
    }

    private void styleSectionHeading(XWPFParagraph paragraph, SectionPlan section) {
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setStyle(section.level() == 1 ? "Heading1" : "Heading2");
        XWPFRun run = paragraph.createRun();
        run.setText(section.title());
        run.setBold(true);
        run.setFontFamily(TARGET_FONT);
        run.setFontSize(HEADING_FONT_SIZE);
    }

    private void styleSectionBody(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily(TARGET_FONT);
        run.setFontSize(BODY_FONT_SIZE);
    }

    private void addBodyParagraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        styleSectionBody(paragraph, text);
    }

    private void collectManualActions(List<Violation> violations, List<String> manualActions) {
        for (Violation violation : violations) {
            String code = violation.getRuleCode();
            if (code == null) {
                continue;
            }
            if (code.equals("LANGUAGE.PAST_TENSE")) {
                manualActions.add("Перепишите фразы в прошедшем времени в настоящем времени в абзаце "
                        + safeLine(violation) + ".");
            } else if (code.startsWith("PLAGIARISM")) {
                manualActions.add("Перепишите совпадающие фрагменты своими словами и добавьте ссылки на источники.");
            } else if (code.startsWith("REF") || code.startsWith("FIG")) {
                manualActions.add(violation.getSuggestion() != null
                        ? violation.getSuggestion()
                        : "Проверьте нарушение " + code + " вручную.");
            }
        }
    }

    private String safeLine(Violation violation) {
        return violation.getLineNumber() > 0 ? String.valueOf(violation.getLineNumber()) : "с указанным нарушением";
    }

    private boolean hasAnyCode(List<Violation> violations, String... prefixes) {
        for (Violation violation : violations) {
            String code = violation.getRuleCode();
            if (code == null) {
                continue;
            }
            for (String prefix : prefixes) {
                if (code.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureRuns(XWPFParagraph paragraph) {
        if (paragraph.getRuns().isEmpty() && paragraph.getText() != null && !paragraph.getText().isBlank()) {
            paragraph.createRun().setText(paragraph.getText());
        }
    }

    private boolean applyRunStyle(XWPFRun run, boolean heading, String color) {
        boolean changed = false;
        if (!TARGET_FONT.equals(run.getFontFamily())) {
            run.setFontFamily(TARGET_FONT);
            changed = true;
        }
        int targetSize = heading ? HEADING_FONT_SIZE : BODY_FONT_SIZE;
        if (run.getFontSizeAsDouble() == null || run.getFontSizeAsDouble() != targetSize) {
            run.setFontSize(targetSize);
            changed = true;
        }
        if (color != null) {
            run.setColor(color);
            changed = true;
        }
        return changed;
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null && style.toLowerCase(Locale.ROOT).contains("heading")) {
            return true;
        }
        String text = paragraph.getText();
        return text != null && text.length() <= 120 && text.equals(text.toUpperCase(Locale.ROOT));
    }

    private boolean documentContains(XWPFDocument doc, String text) {
        String needle = normalize(text);
        return doc.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .map(DocumentAutoFixService::normalize)
                .anyMatch(value -> value.contains(needle));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private String replaceIgnoreCase(String text, Map<String, String> replacements) {
        String result = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replaceAll("(?iu)" + Pattern.quote(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private byte[] toBytes(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            return out.toByteArray();
        }
    }

    private record SectionPlan(String code, int level, String title, String templateText, String gostRef) {
    }
}
