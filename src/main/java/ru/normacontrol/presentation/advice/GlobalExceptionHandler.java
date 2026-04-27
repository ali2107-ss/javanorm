package ru.normacontrol.presentation.advice;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.normacontrol.infrastructure.exception.FileSizeLimitExceededException;
import ru.normacontrol.infrastructure.exception.RateLimitExceededException;
import ru.normacontrol.infrastructure.parser.UnsupportedDocumentTypeException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global REST exception handler with unified error payloads.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle invalid request payloads and bean validation failures.
     *
     * @param ex thrown validation exception
     * @param request current HTTP request
     * @return unified error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + defaultMessage(fieldError))
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request, false, ex);
    }

    /**
     * Handle authentication failures.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle bad request arguments.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle authorization failures.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle missing entities.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle payloads exceeding configured file size.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler({FileSizeLimitExceededException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleFileTooLarge(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MaxUploadSizeExceededException
                ? "Размер файла превышает допустимый лимит"
                : ex.getMessage();
        return build(HttpStatus.PAYLOAD_TOO_LARGE, message, request, false, ex);
    }

    /**
     * Handle rate-limiting errors.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle unsupported document formats.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedDocumentType(UnsupportedDocumentTypeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request, false, ex);
    }

    /**
     * Handle all unexpected server-side errors.
     *
     * @param ex thrown exception
     * @param request current request
     * @return unified error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request, true, ex);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String message,
                                                HttpServletRequest request,
                                                boolean withStacktrace,
                                                Exception ex) {
        String path = request != null ? request.getRequestURI() : "";
        String traceId = MDC.get("traceId");
        if (withStacktrace) {
            log.error("{} {}", status.value(), message, ex);
        } else {
            log.warn("{} {}", status.value(), message);
        }
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                traceId
        ));
    }

    private String defaultMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "невалидное значение";
    }
}
