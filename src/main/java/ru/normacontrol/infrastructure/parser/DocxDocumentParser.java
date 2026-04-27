package ru.normacontrol.infrastructure.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DOCX parser based on Apache POI.
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.DOCX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsedDocument parse(InputStream stream) {
        try (XWPFDocument document = new XWPFDocument(stream)) {
            StringBuilder fullText = new StringBuilder();
            List<ParsedSection> sections = new ArrayList<>();
            List<ParsedTable> tables = new ArrayList<>();
            List<String> figureCaptions = new ArrayList<>();
            Map<String, String> metadata = new LinkedHashMap<>();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                fullText.append(text).append(System.lineSeparator());
                if (isHeading(paragraph)) {
                    sections.add(new ParsedSection(text.trim(), ""));
                }
                if (text.trim().matches("(?i)^рисунок\\s+\\d+.*")) {
                    figureCaptions.add(text.trim());
                }
            }

            for (XWPFTable table : document.getTables()) {
                List<String> rows = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    rows.add(row.getCell(0) != null ? row.getCell(0).getText() : "");
                }
                tables.add(new ParsedTable("", rows));
            }

            metadata.put("paragraphCount", Integer.toString(document.getParagraphs().size()));
            metadata.put("tableCount", Integer.toString(document.getTables().size()));

            return new ParsedDocument(fullText.toString(), sections, tables, figureCaptions, metadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse DOCX document", ex);
        }
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && style.toLowerCase().contains("heading");
    }
}
