package ru.normacontrol.domain.entity;

import lombok.*;
import ru.normacontrol.domain.enums.ViolationSeverity;
import java.util.UUID;

/**
 * Доменная сущность нарушения ГОСТ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {
    private UUID id;
    private String ruleCode;
    private ViolationSeverity severity;
    private String message;
    private String location;
    private String suggestion;
}
