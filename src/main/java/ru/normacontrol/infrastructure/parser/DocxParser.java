package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Парсер DOCX-документов на основе Apache POI.
 * Извлекает текст и метаданные оформления.
 */
@Slf4j
@Component
public class DocxParser {

    // 1 twip = 1/1440 дюйма, 1 дюйм = 25.4 мм → 1 twip ≈ 0.01764 мм
    private static final double TWIPS_TO_MM = 25.4 / 1440.0;
    // Half-points to points
    private static final double HALF_POINTS_TO_PT = 0.5;

    public DocumentParser.ParsedDocument parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder textBuilder = new StringBuilder();
            double detectedFontSize = 0;
            String detectedFontName = "";
            double lineSpacing = 0;

            // Извлечение текста и метаданных из параграфов
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                textBuilder.append(paragraph.getText()).append("\n");

                // Определение шрифта из первого run с данными
                if (detectedFontSize == 0) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        if (run.getFontSizeAsDouble() != null && run.getFontSizeAsDouble() > 0) {
                            detectedFontSize = run.getFontSizeAsDouble();
                            detectedFontName = run.getFontFamily() != null
                                    ? run.getFontFamily() : "";
                            break;
                        }
                    }
                }

                // Определение межстрочного интервала
                if (lineSpacing == 0 && paragraph.getSpacingBetween() > 0) {
                    lineSpacing = paragraph.getSpacingBetween();
                }
            }

            // Извлечение текста из таблиц
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        textBuilder.append(cell.getText()).append(" ");
                    }
                    textBuilder.append("\n");
                }
            }

            // Извлечение полей страницы
            double marginLeft = 0, marginRight = 0, marginTop = 0, marginBottom = 0;
            CTBody body = document.getDocument().getBody();
            if (body != null) {
                CTSectPr sectPr = body.getSectPr();
                if (sectPr != null) {
                    CTPageMar pgMar = sectPr.getPgMar();
                    if (pgMar != null) {
                        marginLeft = twipsToMm(pgMar.getLeft());
                        marginRight = twipsToMm(pgMar.getRight());
                        marginTop = twipsToMm(pgMar.getTop());
                        marginBottom = twipsToMm(pgMar.getBottom());
                    }
                }
            }

            // Количество страниц (приблизительно)
            int pageCount = document.getProperties().getExtendedProperties()
                    .getUnderlyingProperties().getPages();
            if (pageCount <= 0) pageCount = 1;

            // Проверка нумерации страниц (наличие header/footer с номерами)
            boolean hasPageNumbers = !document.getHeaderList().isEmpty()
                    || !document.getFooterList().isEmpty();

            String fullText = textBuilder.toString();
            log.info("DOCX распарсен: {} символов, {} страниц, шрифт: {} {}pt",
                    fullText.length(), pageCount, detectedFontName, detectedFontSize);

            return new DocumentParser.ParsedDocument(
                    fullText,
                    detectedFontSize,
                    detectedFontName,
                    marginLeft,
                    marginRight,
                    marginTop,
                    marginBottom,
                    lineSpacing,
                    pageCount,
                    hasPageNumbers
            );

        } catch (Exception e) {
            log.error("Ошибка парсинга DOCX: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка парсинга DOCX-документа", e);
        }
    }

    private double twipsToMm(Object twipsObj) {
        if (twipsObj == null) return 0;
        long twips;
        if (twipsObj instanceof BigInteger) {
            twips = ((BigInteger) twipsObj).longValue();
        } else {
            twips = Long.parseLong(twipsObj.toString());
        }
        return twips * TWIPS_TO_MM;
    }
}
