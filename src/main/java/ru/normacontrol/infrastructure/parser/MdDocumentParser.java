package ru.normacontrol.infrastructure.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Markdown parser.
 */
@Component
public class MdDocumentParser implements DocumentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.MD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsedDocument parse(InputStream stream) {
        try {
            String fullText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<ParsedSection> sections = Arrays.stream(fullText.split("\\R"))
                    .filter(line -> line.startsWith("#"))
                    .map(line -> new ParsedSection(line.replaceFirst("^#+\\s*", ""), ""))
                    .toList();
            return new ParsedDocument(fullText, sections, List.of(), List.of(), Map.of("format", "md"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse Markdown document", ex);
        }
    }
}
