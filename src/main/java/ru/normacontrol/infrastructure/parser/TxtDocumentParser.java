package ru.normacontrol.infrastructure.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Plain-text parser.
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.TXT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsedDocument parse(InputStream stream) {
        try {
            String fullText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<ParsedSection> sections = Arrays.stream(fullText.split("\\R\\R+"))
                    .filter(block -> !block.isBlank())
                    .map(block -> new ParsedSection(firstLine(block), block))
                    .toList();
            return new ParsedDocument(fullText, sections, List.of(), List.of(), Map.of("format", "txt"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse TXT document", ex);
        }
    }

    private String firstLine(String block) {
        String[] lines = block.split("\\R", 2);
        return lines.length == 0 ? "" : lines[0];
    }
}
