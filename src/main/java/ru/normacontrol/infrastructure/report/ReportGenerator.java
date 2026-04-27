package ru.normacontrol.infrastructure.report;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;
import ru.normacontrol.infrastructure.minio.MinioStorageService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Generates branded PDF reports and uploads them to MinIO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private static final Color DARK_BLUE = new DeviceRgb(0x1F, 0x38, 0x64);
    private static final Color GREEN = new DeviceRgb(0x27, 0xAE, 0x60);
    private static final Color YELLOW = new DeviceRgb(0xF3, 0x9C, 0x12);
    private static final Color RED = new DeviceRgb(0xE7, 0x4C, 0x3C);
    private static final Color BLUE = new DeviceRgb(0x34, 0x98, 0xDB);
    private static final Color LIGHT_BLUE = new DeviceRgb(0xEB, 0xF5, 0xFB);
    private static final Color LIGHT_GRAY = new DeviceRgb(0xF5, 0xF5, 0xF5);
    private static final Color BORDER_GRAY = new DeviceRgb(0xDD, 0xDD, 0xDD);
    private static final Color FOOTER_BLUE = new DeviceRgb(0x2E, 0x74, 0xB5);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru-RU"));

    private final MinioStorageService storageService;
    private final MeterRegistry meterRegistry;

    /**
     * Generate a PDF report for a completed check and upload it to MinIO.
     *
     * @param result completed check result
     * @param sourceDocument source document
     * @return uploaded object path in MinIO
     */
    public String generatePdfReport(CheckResult result, ru.normacontrol.domain.entity.Document sourceDocument) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.currentTimeMillis();
        String reportPath = "reports/%s/%s.pdf".formatted(sourceDocument.getId(), result.getId());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(outputStream);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document pdf = new Document(pdfDocument, PageSize.A4)) {

            PdfFont regular = loadFont(false);
            PdfFont bold = loadFont(true);
            pdf.setFont(regular);
            pdf.setMargins(36, 36, 36, 36);

            addCoverPage(pdfDocument, pdf, regular, bold, result, sourceDocument);
            addSummaryPage(pdfDocument, pdf, regular, bold, result, sourceDocument);
            addViolationsPages(pdf, regular, bold, result);
            addSignaturePage(pdf, regular);

            pdf.flush();
            storageService.upload(reportPath, outputStream.toByteArray(), "application/pdf");
        } catch (Exception ex) {
            throw new RuntimeException("Не удалось сформировать PDF-отчёт", ex);
        } finally {
            sample.stop(meterRegistry.timer("normacontrol.report.generation.time"));
        }

        log.info("PDF отчёт сгенерирован за {}мс, путь: {}", System.currentTimeMillis() - startedAt, reportPath);
        return reportPath;
    }

    private void addCoverPage(PdfDocument pdfDocument,
                              Document pdf,
                              PdfFont regular,
                              PdfFont bold,
                              CheckResult result,
                              ru.normacontrol.domain.entity.Document sourceDocument) {
        PdfPage page = pdfDocument.addNewPage();
        Rectangle pageSize = page.getPageSize();
        PdfCanvas pdfCanvas = new PdfCanvas(page);

        pdfCanvas.saveState()
                .setFillColor(DARK_BLUE)
                .rectangle(0, pageSize.getHeight() * 0.60f, pageSize.getWidth(), pageSize.getHeight() * 0.40f)
                .fill()
                .restoreState();

        Canvas topCanvas = new Canvas(pdfCanvas, new Rectangle(36, pageSize.getHeight() * 0.63f, pageSize.getWidth() - 72, 200));
        topCanvas.add(new Paragraph("ОТЧЁТ О НОРМОКОНТРОЛЬНОЙ ПРОВЕРКЕ")
                .setFont(bold).setFontSize(28).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER));
        topCanvas.add(new Paragraph(sourceDocument.getOriginalFilename())
                .setFont(regular).setFontSize(16).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER));
        topCanvas.add(new Paragraph("Дата: %s  |  ГОСТ 19.201-78".formatted(formatDateTime(result)))
                .setFont(regular).setFontSize(14).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER));
        topCanvas.close();

        int score = safeScore(result);
        float centerX = pageSize.getWidth() / 2;
        float centerY = pageSize.getHeight() / 2 + 20;
        pdfCanvas.saveState()
                .setFillColor(scoreColor(score))
                .circle(centerX, centerY, 75)
                .fill()
                .restoreState();

        Canvas scoreCanvas = new Canvas(pdfCanvas, pageSize);
        scoreCanvas.showTextAligned(new Paragraph(String.valueOf(score))
                        .setFont(bold).setFontSize(72).setFontColor(ColorConstants.WHITE),
                centerX, centerY + 12, TextAlignment.CENTER);
        scoreCanvas.showTextAligned(new Paragraph("/100")
                        .setFont(regular).setFontSize(24).setFontColor(ColorConstants.WHITE),
                centerX, centerY - 30, TextAlignment.CENTER);
        scoreCanvas.showTextAligned(new Paragraph(scoreLabel(score))
                        .setFont(bold).setFontSize(14).setFontColor(scoreColor(score)),
                centerX, centerY - 105, TextAlignment.CENTER);

        Table stats = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setFixedPosition(1, 36, 70, pageSize.getWidth() - 72);
        stats.addCell(statCell("🔴 КРИТИЧНО: " + countBySeverity(result, ViolationSeverity.CRITICAL), RED, bold));
        stats.addCell(statCell("🟡 ПРЕДУПРЕЖДЕНИЙ: " + countBySeverity(result, ViolationSeverity.WARNING), YELLOW, bold));
        stats.addCell(statCell("ℹ ИНФОРМАЦИЯ: " + countBySeverity(result, ViolationSeverity.INFO), BLUE, bold));
        pdf.add(stats);
    }

    private void addSummaryPage(PdfDocument pdfDocument,
                                Document pdf,
                                PdfFont regular,
                                PdfFont bold,
                                CheckResult result,
                                ru.normacontrol.domain.entity.Document sourceDocument) {
        pdf.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        pdf.add(new Paragraph("СВЕДЕНИЯ О ПРОВЕРКЕ")
                .setFont(bold).setFontSize(20).setMarginBottom(20));

        Table summary = new Table(UnitValue.createPercentArray(new float[]{2, 3})).useAllAvailableWidth();
        addSummaryRow(summary, "Документ:", sourceDocument.getOriginalFilename(), regular, bold, true);
        addSummaryRow(summary, "Размер файла:", (sourceDocument.getFileSize() != null ? sourceDocument.getFileSize() / 1024 : 0) + " КБ", regular, bold, false);
        addSummaryRow(summary, "Формат:", resolveDocumentType(sourceDocument), regular, bold, true);
        addSummaryRow(summary, "Набор правил:", "%s версия %s".formatted(orDefault(result.getRuleSetName(), "ГОСТ 19.201-78"), orDefault(result.getRuleSetVersion(), "1.0")), regular, bold, false);
        addSummaryRow(summary, "Время обработки:", orDefault(result.getProcessingTimeMs(), 0L) + " мс", regular, bold, true);
        addSummaryRow(summary, "Итоговый балл:", safeScore(result) + " / 100", regular, bold, false);
        addSummaryRow(summary, "Результат:", result.isPassed() ? "ПРОШЁЛ" : "НЕ ПРОШЁЛ", regular, bold, true, result.isPassed() ? GREEN : RED);
        addSummaryRow(summary, "Дата проверки:", formatDateTime(result), regular, bold, false);
        pdf.add(summary);

        pdf.add(new Paragraph("Балл соответствия: %d/100".formatted(safeScore(result)))
                .setFont(bold).setFontSize(12).setMarginTop(24).setMarginBottom(8));

        PdfPage page = pdfDocument.getLastPage();
        PdfCanvas pdfCanvas = new PdfCanvas(page);
        float barX = 72;
        float barY = page.getPageSize().getHeight() - 390;
        float barWidth = 450;
        float barHeight = 25;
        pdfCanvas.saveState()
                .setFillColor(LIGHT_GRAY)
                .rectangle(barX, barY, barWidth, barHeight)
                .fill()
                .restoreState();
        pdfCanvas.saveState()
                .setFillColor(scoreColor(safeScore(result)))
                .rectangle(barX, barY, (safeScore(result) / 100f) * barWidth, barHeight)
                .fill()
                .restoreState();

        pdf.add(new Paragraph(" "));

        Table penalties = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 2}))
                .useAllAvailableWidth()
                .setMarginTop(50);
        addPenaltyHeader(penalties, bold);
        addPenaltyRow(penalties, "КРИТИЧНО", countBySeverity(result, ViolationSeverity.CRITICAL), -10, bold, regular);
        addPenaltyRow(penalties, "ПРЕДУПРЕЖД.", countBySeverity(result, ViolationSeverity.WARNING), -2, bold, regular);
        addPenaltyRow(penalties, "ИНФОРМАЦИЯ", countBySeverity(result, ViolationSeverity.INFO), 0, bold, regular);
        penalties.addCell(new Cell().add(new Paragraph("ИТОГОВЫЙ БАЛЛ").setFont(bold)));
        penalties.addCell(emptyCell());
        penalties.addCell(emptyCell());
        penalties.addCell(new Cell().add(new Paragraph(String.valueOf(safeScore(result))).setFont(bold)));
        pdf.add(penalties);
    }

    private void addViolationsPages(Document pdf,
                                    PdfFont regular,
                                    PdfFont bold,
                                    CheckResult result) {
        pdf.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        pdf.add(new Paragraph("ВЫЯВЛЕННЫЕ НАРУШЕНИЯ (" + result.getViolations().size() + " шт.)")
                .setFont(bold).setFontSize(18).setMarginBottom(16));

        List<Violation> orderedViolations = result.getViolations().stream()
                .sorted(Comparator.comparingInt(v -> severityOrder(v.getSeverity())))
                .toList();

        for (Violation violation : orderedViolations) {
            Table card = new Table(UnitValue.createPercentArray(new float[]{0.04f, 0.96f}))
                    .useAllAvailableWidth()
                    .setKeepTogether(true)
                    .setMarginBottom(12);
            card.addCell(new Cell()
                    .setBackgroundColor(severityColor(violation.getSeverity()))
                    .setBorder(Border.NO_BORDER)
                    .setMinHeight(110));

            Div content = new Div().setPaddingLeft(10).setPaddingRight(10).setPaddingTop(8).setPaddingBottom(8);
            Table titleRow = new Table(UnitValue.createPercentArray(new float[]{0.75f, 0.25f})).useAllAvailableWidth();
            titleRow.addCell(new Cell().add(new Paragraph(violation.getRuleCode()).setFont(bold).setFontSize(11)).setBorder(Border.NO_BORDER));
            titleRow.addCell(new Cell()
                    .add(new Paragraph(severityLabel(violation.getSeverity()))
                            .setFont(bold)
                            .setFontSize(9)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(severityColor(violation.getSeverity()))
                    .setBorder(Border.NO_BORDER));
            content.add(titleRow);

            if (violation.getPageNumber() > 0) {
                content.add(new Paragraph("Страница документа: " + violation.getPageNumber())
                        .setFont(regular)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginTop(4)
                        .setMarginBottom(4));
            }

            content.add(new Paragraph(violation.getDescription())
                    .setFont(regular)
                    .setFontSize(11)
                    .setMarginBottom(8));

            content.add(infoBlock("📌 Стандарт", violation.getRuleReference(), LIGHT_GRAY, regular, true));
            content.add(infoBlock("💡 Рекомендация",
                    violation.getAiSuggestion() != null && !violation.getAiSuggestion().isBlank()
                            ? violation.getAiSuggestion()
                            : violation.getSuggestion(),
                    LIGHT_BLUE, regular, false));

            card.addCell(new Cell().add(content).setBorder(Border.NO_BORDER));
            pdf.add(card);
            pdf.add(new LineSeparator(new SolidLine(0.5f)).setStrokeColor(BORDER_GRAY).setMarginBottom(10));
        }
    }

    private void addSignaturePage(Document pdf, PdfFont regular) {
        pdf.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        pdf.add(new LineSeparator(new SolidLine(1f)).setStrokeColor(FOOTER_BLUE).setMarginTop(180).setMarginBottom(24));
        pdf.add(new Paragraph("Отчёт сформирован автоматически системой НормаКонтроль v1.0")
                .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
        pdf.add(new Paragraph("Разработано в рамках курса «Продвинутая Java» · Идаят Али · 2025")
                .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
        pdf.add(new Paragraph("Набор правил: ГОСТ 19.201-78 «Техническое задание»")
                .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private Cell statCell(String text, Color color, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold).setFontColor(color).setTextAlignment(TextAlignment.CENTER))
                .setBorder(new SolidBorder(BORDER_GRAY, 1))
                .setPadding(10)
                .setBackgroundColor(ColorConstants.WHITE);
    }

    private void addSummaryRow(Table table,
                               String label,
                               String value,
                               PdfFont regular,
                               PdfFont bold,
                               boolean alternate) {
        addSummaryRow(table, label, value, regular, bold, alternate, null);
    }

    private void addSummaryRow(Table table,
                               String label,
                               String value,
                               PdfFont regular,
                               PdfFont bold,
                               boolean alternate,
                               Color valueColor) {
        Color background = alternate ? LIGHT_GRAY : ColorConstants.WHITE;
        table.addCell(new Cell().add(new Paragraph(label).setFont(bold)).setBackgroundColor(background));
        Paragraph valueParagraph = new Paragraph(value).setFont(regular);
        if (valueColor != null) {
            valueParagraph.setFontColor(valueColor);
        }
        table.addCell(new Cell().add(valueParagraph).setBackgroundColor(background));
    }

    private void addPenaltyHeader(Table table, PdfFont bold) {
        table.addHeaderCell(new Cell().add(new Paragraph("Уровень").setFont(bold)).setBackgroundColor(LIGHT_GRAY));
        table.addHeaderCell(new Cell().add(new Paragraph("Количество").setFont(bold)).setBackgroundColor(LIGHT_GRAY));
        table.addHeaderCell(new Cell().add(new Paragraph("Штраф за каждое").setFont(bold)).setBackgroundColor(LIGHT_GRAY));
        table.addHeaderCell(new Cell().add(new Paragraph("Итого штраф").setFont(bold)).setBackgroundColor(LIGHT_GRAY));
    }

    private void addPenaltyRow(Table table, String level, long count, int penalty, PdfFont bold, PdfFont regular) {
        table.addCell(new Cell().add(new Paragraph(level).setFont(bold)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regular)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(penalty)).setFont(regular)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(count * penalty)).setFont(regular)));
    }

    private Cell emptyCell() {
        return new Cell().add(new Paragraph("")).setBorder(Border.NO_BORDER);
    }

    private Div infoBlock(String title, String value, Color background, PdfFont regular, boolean italic) {
        Div block = new Div()
                .setBackgroundColor(background)
                .setPadding(8)
                .setMarginBottom(6);
        block.add(new Paragraph(title).setBold().setFontSize(10));
        Paragraph text = new Paragraph(orDefault(value, "Не указано")).setFont(regular).setFontSize(10).setMarginBottom(0);
        if (italic) {
            text.setItalic();
        }
        block.add(text);
        return block;
    }

    private String formatDateTime(CheckResult result) {
        return result.getCheckedAt() != null ? result.getCheckedAt().format(DATE_TIME_FORMATTER) : "не указана";
    }

    private long countBySeverity(CheckResult result, ViolationSeverity severity) {
        return result.getViolations().stream().filter(v -> v.getSeverity() == severity).count();
    }

    private int safeScore(CheckResult result) {
        return result.getComplianceScore() != null ? result.getComplianceScore() : result.calculateScore();
    }

    private Color scoreColor(int score) {
        if (score >= 80) {
            return GREEN;
        }
        if (score >= 60) {
            return YELLOW;
        }
        return RED;
    }

    private String scoreLabel(int score) {
        if (score >= 80) {
            return "✓ СООТВЕТСТВУЕТ ТРЕБОВАНИЯМ";
        }
        if (score >= 60) {
            return "⚠ УСЛОВНО СООТВЕТСТВУЕТ";
        }
        return "✗ НЕ СООТВЕТСТВУЕТ ТРЕБОВАНИЯМ";
    }

    private int severityOrder(ViolationSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    private Color severityColor(ViolationSeverity severity) {
        return switch (severity) {
            case CRITICAL -> RED;
            case WARNING -> YELLOW;
            case INFO -> BLUE;
        };
    }

    private String severityLabel(ViolationSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "КРИТИЧНО";
            case WARNING -> "ПРЕДУПРЕЖДЕНИЕ";
            case INFO -> "ИНФОРМАЦИЯ";
        };
    }

    private String resolveDocumentType(ru.normacontrol.domain.entity.Document sourceDocument) {
        String fileName = sourceDocument.getOriginalFilename() != null ? sourceDocument.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (fileName.endsWith(".docx")) {
            return "DOCX";
        }
        if (fileName.endsWith(".pdf")) {
            return "PDF";
        }
        if (fileName.endsWith(".md")) {
            return "MD";
        }
        if (fileName.endsWith(".txt")) {
            return "TXT";
        }
        return orDefault(sourceDocument.getContentType(), "UNKNOWN");
    }

    private String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private Long orDefault(Long value, Long fallback) {
        return value != null ? value : fallback;
    }

    private PdfFont loadFont(boolean bold) throws IOException {
        List<String> candidates = List.of(
                "C:/Windows/Fonts/arialbd.ttf",
                "C:/Windows/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf"
        );

        for (String candidate : candidates) {
            if (Files.exists(Path.of(candidate))) {
                boolean isBoldCandidate = candidate.toLowerCase(Locale.ROOT).contains("bold")
                        || candidate.toLowerCase(Locale.ROOT).contains("bd");
                if (bold == isBoldCandidate || (!bold && !isBoldCandidate)) {
                    return PdfFontFactory.createFont(candidate, PdfEncodings.IDENTITY_H);
                }
            }
        }

        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
    }
}
