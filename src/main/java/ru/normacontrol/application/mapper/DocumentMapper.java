package ru.normacontrol.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.domain.entity.Document;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "status", expression = "java(document.getStatus().name())")
    DocumentResponse toResponse(Document document);
}
