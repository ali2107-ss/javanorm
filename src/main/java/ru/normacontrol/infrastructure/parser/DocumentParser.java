package ru.normacontrol.infrastructure.parser;

import java.io.InputStream;

/**
 * Parser contract for a specific document type.
 */
public interface DocumentParser {

    /**
     * Check whether this parser supports the provided type.
     *
     * @param type document type
     * @return {@code true} when the parser supports the type
     */
    boolean supports(DocumentType type);

    /**
     * Parse the incoming stream.
     *
     * @param stream document content stream
     * @return parsed document model
     */
    ParsedDocument parse(InputStream stream);
}
