package ru.normacontrol.infrastructure.parser;

/**
 * Thrown when no parser supports the requested document type.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    /**
     * Create exception with message.
     *
     * @param message error message
     */
    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
