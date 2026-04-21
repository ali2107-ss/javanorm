package ru.normacontrol.domain.entity;

import lombok.*;
import ru.normacontrol.domain.enums.DocumentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Доменная сущность документа.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private UUID id;
    private String originalFilename;
    private String storageKey;
    private String contentType;
    private Long fileSize;
    private DocumentStatus status;
    private UUID ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Перевести документ в статус "в очереди на проверку".
     */
    public void enqueue() {
        this.status = DocumentStatus.QUEUED;
    }

    /**
     * Перевести документ в статус "проверяется".
     */
    public void startChecking() {
        this.status = DocumentStatus.CHECKING;
    }

    /**
     * Перевести документ в статус "проверен".
     */
    public void markChecked() {
        this.status = DocumentStatus.CHECKED;
    }

    /**
     * Перевести документ в статус "ошибка".
     */
    public void markFailed() {
        this.status = DocumentStatus.FAILED;
    }
}
