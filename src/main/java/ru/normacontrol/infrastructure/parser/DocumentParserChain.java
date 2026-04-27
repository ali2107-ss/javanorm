package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Chain of responsibility for document parsers.
 */
@Slf4j
@Component
public class DocumentParserChain {

    private final List<DocumentParser> parsers;

    /**
     * Create parser chain.
     *
     * @param parsers available parsers
     */
    public DocumentParserChain(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /**
     * Parse a document using the first parser that supports its type.
     *
     * @param stream source content stream
     * @param type document type
     * @return parsed document
     * @throws UnsupportedDocumentTypeException when no parser supports the type
     */
    public ParsedDocument parse(InputStream stream, DocumentType type) {
        if (stream == null) {
            throw new IllegalArgumentException("Stream must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Document type must not be null");
        }

        for (DocumentParser parser : parsers) {
            if (parser.supports(type)) {
                log.info("Using {} for {}", parser.getClass().getSimpleName(), type);
                return parser.parse(stream);
            }
        }

        throw new UnsupportedDocumentTypeException("Unsupported document type: " + type);
    }
}
