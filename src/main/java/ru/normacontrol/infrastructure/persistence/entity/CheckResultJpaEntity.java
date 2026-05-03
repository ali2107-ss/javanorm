package ru.normacontrol.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "check_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResultJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentJpaEntity document;

    @Column(name = "rule_set_name", nullable = false, length = 100)
    private String ruleSetName;

    @Column(name = "rule_set_version", length = 20)
    private String ruleSetVersion;

    @Column(name = "compliance_score", nullable = false)
    private int complianceScore;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "report_storage_path", length = 1000)
    private String reportStoragePath;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "uniqueness_percent")
    private Integer uniquenessPercent;

    @Column(name = "plagiarism_result", columnDefinition = "jsonb")
    private String plagiarismResult;

    @OneToMany(mappedBy = "checkResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ViolationJpaEntity> violations = new ArrayList<>();
}
