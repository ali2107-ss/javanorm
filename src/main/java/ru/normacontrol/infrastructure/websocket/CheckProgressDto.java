package ru.normacontrol.infrastructure.websocket;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Record для передачи прогресса проверки на клиент по WebSocket.
 *
 * @param documentId UUID документа
 * @param percent процент завершения (0-100)
 * @param stage текущий этап (DOWNLOADING/PARSING/CHECKING/GENERATING_REPORT/DONE/FAILED)
 * @param message читаемое сообщение для пользователя
 * @param timestamp время события
 */
public record CheckProgressDto(
        UUID documentId,
        int percent,
        String stage,
        String message,
        LocalDateTime timestamp
) {
    public CheckProgressDto(UUID documentId, int percent, String stage, String message) {
        this(documentId, percent, stage, message, LocalDateTime.now());
    }
}
