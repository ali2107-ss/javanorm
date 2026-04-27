package ru.normacontrol.infrastructure.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * PDF parser based on Apache PDFBox.
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PDF;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsedDocument parse(InputStream stream) {
        try {
            byte[] bytes = stream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                String fullText = new PDFTextStripper().getText(document);
                return new ParsedDocument(
                        fullText,
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of("pageCount", Integer.toString(document.getNumberOfPages()))
                );
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse PDF document", ex);
        }
    }
}
