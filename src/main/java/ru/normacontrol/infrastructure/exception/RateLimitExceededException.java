package ru.normacontrol.infrastructure.exception;

/**
 * Thrown when request rate limits are exceeded.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Create exception with message.
     *
     * @param message error message
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
}
