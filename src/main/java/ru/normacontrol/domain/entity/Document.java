package ru.normacontrol.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.normacontrol.domain.enums.DocumentStatus;
import ru.normacontrol.domain.event.DocumentDeletedEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain aggregate representing an uploaded document.
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
     * Return original file name.
     *
     * @return file name
     */
    public String getFileName() {
        return originalFilename;
    }

    /**
     * Mark document as queued for checking.
     */
    public void enqueue() {
        status = DocumentStatus.QUEUED;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark document as currently being checked.
     */
    public void startChecking() {
        status = DocumentStatus.CHECKING;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark document as checked.
     */
    public void markChecked() {
        status = DocumentStatus.CHECKED;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark document as failed.
     */
    public void markFailed() {
        status = DocumentStatus.FAILED;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Soft-delete document and create a domain event.
     *
     * @param userId user who deleted the document
     * @return deletion event
     */
    public DocumentDeletedEvent softDelete(UUID userId) {
        status = DocumentStatus.DELETED;
        updatedAt = LocalDateTime.now();
        return new DocumentDeletedEvent(id, userId, originalFilename);
    }
}
