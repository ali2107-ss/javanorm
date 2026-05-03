package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResultResponse {
    private UUID id;
    private UUID documentId;
    private boolean passed;
    private int totalViolations;
    private String summary;
    private LocalDateTime checkedAt;
    private UUID checkedBy;
    private Integer complianceScore;
    private String ruleSetName;
    private String ruleSetVersion;
    private Long processingTimeMs;
    private String reportStoragePath;
    private Integer uniquenessPercent;
    private ru.normacontrol.infrastructure.plagiarism.PlagiarismResult plagiarismResult;
    private List<ViolationResponse> violations;
}
