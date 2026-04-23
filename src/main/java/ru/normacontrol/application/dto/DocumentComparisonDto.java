package ru.normacontrol.application.dto;

import lombok.Builder;
import java.util.UUID;
import java.util.List;

@Builder
public record DocumentComparisonDto(
    UUID docV1Id,
    UUID docV2Id,
    int scoreV1,
    int scoreV2,
    int scoreImprovement,
    List<ViolationDto> fixedViolations,
    List<ViolationDto> newViolations,
    List<ViolationDto> persistentViolations,
    String summary
) {}
