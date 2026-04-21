package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationResponse {
    private UUID id;
    private String ruleCode;
    private String severity;
    private String message;
    private String location;
    private String suggestion;
}
