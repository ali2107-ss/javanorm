package ru.normacontrol.presentation.advice;

import java.time.LocalDateTime;

/**
 * Unified API error payload.
 *
 * @param timestamp error timestamp
 * @param status HTTP status code
 * @param error HTTP reason phrase
 * @param message human-readable message
 * @param path request path
 * @param traceId per-request trace identifier
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId
) {
}
