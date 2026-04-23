package ru.normacontrol.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompareRequest(
    @NotNull(message = "ID документа V1 не может быть пустым")
    UUID documentV1Id,
    
    @NotNull(message = "ID документа V2 не может быть пустым")
    UUID documentV2Id
) {}
