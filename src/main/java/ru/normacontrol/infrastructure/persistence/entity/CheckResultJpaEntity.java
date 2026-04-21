package ru.normacontrol.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
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

    @Column(name = "document_id", nullable = false, columnDefinition = "UUID")
    private UUID documentId;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "total_violations")
    private int totalViolations;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "checked_by", columnDefinition = "UUID")
    private UUID checkedBy;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @OneToMany(mappedBy = "checkResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ViolationJpaEntity> violations = new ArrayList<>();
}
