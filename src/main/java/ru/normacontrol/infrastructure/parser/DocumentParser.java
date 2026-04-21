package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Универсальный парсер документов.
 * Определяет тип файла и делегирует парсинг соответствующему обработчику.
 */
@Slf4j
@Component
public class DocumentParser {

    private final DocxParser docxParser;
    private final PdfParser pdfParser;

    public DocumentParser(DocxParser docxParser, PdfParser pdfParser) {
        this.docxParser = docxParser;
        this.pdfParser = pdfParser;
    }

    /**
     * Распарсить документ из InputStream.
     *
     * @param inputStream  поток данных файла
     * @param contentType  MIME-тип файла
     * @return ParsedDocument с текстом и метаданными
     */
    public ParsedDocument parse(InputStream inputStream, String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Content type не указан");
        }

        if (contentType.contains("pdf")) {
            log.info("Парсинг PDF-документа");
            return pdfParser.parse(inputStream);
        } else if (contentType.contains("wordprocessingml") || contentType.contains("msword")) {
            log.info("Парсинг DOCX-документа");
            return docxParser.parse(inputStream);
        } else {
            throw new IllegalArgumentException(
                    "Неподдерживаемый тип файла: " + contentType);
        }
    }

    /**
     * Результат парсинга документа.
     */
    public record ParsedDocument(
            String text,
            double fontSize,
            String fontName,
            double marginLeft,
            double marginRight,
            double marginTop,
            double marginBottom,
            double lineSpacing,
            int pageCount,
            boolean hasPageNumbers
    ) {}
}
