package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Парсер PDF-документов на основе Apache PDFBox.
 * Извлекает текст и базовые метаданные.
 */
@Slf4j
@Component
public class PdfParser {

    // 1 point = 0.3528 мм
    private static final double POINTS_TO_MM = 0.3528;

    public DocumentParser.ParsedDocument parse(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                int pageCount = document.getNumberOfPages();

                // Метаданные из первой страницы
                double marginLeft = 0, marginRight = 0, marginTop = 0, marginBottom = 0;
                if (pageCount > 0) {
                    PDPage firstPage = document.getPage(0);
                    PDRectangle mediaBox = firstPage.getMediaBox();
                    PDRectangle cropBox = firstPage.getCropBox();

                    if (cropBox != null && mediaBox != null) {
                        marginLeft = (cropBox.getLowerLeftX() - mediaBox.getLowerLeftX()) * POINTS_TO_MM;
                        marginBottom = (cropBox.getLowerLeftY() - mediaBox.getLowerLeftY()) * POINTS_TO_MM;
                        marginRight = (mediaBox.getUpperRightX() - cropBox.getUpperRightX()) * POINTS_TO_MM;
                        marginTop = (mediaBox.getUpperRightY() - cropBox.getUpperRightY()) * POINTS_TO_MM;
                    }
                }

                // В PDF сложно определить шрифт программно без глубокого анализа,
                // поэтому возвращаем 0 — движок GOST пропустит эти проверки
                boolean hasPageNumbers = checkPageNumbers(text, pageCount);

                log.info("PDF распарсен: {} символов, {} страниц", text.length(), pageCount);

                return new DocumentParser.ParsedDocument(
                        text,
                        0,      // fontSize — не определяется в PDF простым способом
                        "",     // fontName
                        marginLeft,
                        marginRight,
                        marginTop,
                        marginBottom,
                        0,      // lineSpacing
                        pageCount,
                        hasPageNumbers
                );
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка парсинга PDF-документа", e);
        }
    }

    /**
     * Простая эвристика: проверяем наличие числовой нумерации на страницах.
     */
    private boolean checkPageNumbers(String fullText, int pageCount) {
        if (pageCount <= 1) return true;
        // Ищем последовательные числа, которые могут быть номерами страниц
        for (int i = 2; i <= Math.min(pageCount, 5); i++) {
            if (fullText.contains("\n" + i + "\n") || fullText.contains(" " + i + " ")) {
                return true;
            }
        }
        return false;
    }
}
