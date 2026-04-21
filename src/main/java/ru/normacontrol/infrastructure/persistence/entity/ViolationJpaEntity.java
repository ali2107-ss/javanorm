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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 255)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_result_id", nullable = false)
    private CheckResultJpaEntity checkResult;
}
