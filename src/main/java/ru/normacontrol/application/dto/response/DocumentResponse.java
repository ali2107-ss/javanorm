package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String status;
    private UUID ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
