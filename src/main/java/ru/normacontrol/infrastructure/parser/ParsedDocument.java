package ru.normacontrol.infrastructure.parser;

import java.util.List;
import java.util.Map;

/**
 * Parsed document aggregate.
 *
 * @param fullText full plain text
 * @param sections parsed sections
 * @param tables parsed tables
 * @param figureCaptions parsed figure captions
 * @param metadata parsed metadata map
 */
public record ParsedDocument(
        String fullText,
        List<ParsedSection> sections,
        List<ParsedTable> tables,
        List<String> figureCaptions,
        Map<String, String> metadata
) {
}
