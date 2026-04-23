package ru.normacontrol.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.normacontrol.domain.enums.ViolationSeverity;
import java.util.UUID;

@Entity
@Table(name = "violations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationSeverity severity;

    @Column(name = "page_number")
    private int pageNumber;

    @Column(name = "line_number")
    private int lineNumber;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    @Column(name = "rule_reference", length = 255)
    private String ruleReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_result_id", nullable = false)
    private CheckResultJpaEntity checkResult;
}
