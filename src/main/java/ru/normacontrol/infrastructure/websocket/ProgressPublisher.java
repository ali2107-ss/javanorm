package ru.normacontrol.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Сервис для публикации прогресса проверки в WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Опубликовать прогресс для конкретного документа.
     *
     * @param documentId UUID документа
     * @param percent процент завершения
     * @param stage этап
     * @param message описание этапа
     */
    public void publishProgress(UUID documentId, int percent, String stage, String message) {
        CheckProgressDto payload = new CheckProgressDto(documentId, percent, stage, message);
        String destination = "/topic/check/" + documentId;
        
        log.debug("Отправка WebSocket прогресса [{}]: {}% - {}", documentId, percent, message);
        messagingTemplate.convertAndSend(destination, payload);
    }
}
