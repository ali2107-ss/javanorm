package ru.normacontrol.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.dto.response.ViolationResponse;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;

@Mapper(componentModel = "spring")
public interface CheckResultMapper {

    CheckResultResponse toResponse(CheckResult checkResult);

    @Mapping(target = "severity", expression = "java(violation.getSeverity().name())")
    ViolationResponse violationToResponse(Violation violation);
}
