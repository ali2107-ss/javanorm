package ru.normacontrol.infrastructure.exception;

/**
 * Thrown when an uploaded file exceeds configured limits.
 */
public class FileSizeLimitExceededException extends RuntimeException {

    /**
     * Create exception with message.
     *
     * @param message error message
     */
    public FileSizeLimitExceededException(String message) {
        super(message);
    }
}
