package ru.normacontrol.application.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.dto.response.DocumentResponse;
import ru.normacontrol.domain.entity.Document;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-02T16:22:49+0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class DocumentMapperImpl implements DocumentMapper {

    @Override
    public DocumentResponse toResponse(Document document) {
        if ( document == null ) {
            return null;
        }

        DocumentResponse.DocumentResponseBuilder documentResponse = DocumentResponse.builder();

        documentResponse.contentType( document.getContentType() );
        documentResponse.createdAt( document.getCreatedAt() );
        documentResponse.fileSize( document.getFileSize() );
        documentResponse.id( document.getId() );
        documentResponse.originalFilename( document.getOriginalFilename() );
        documentResponse.ownerId( document.getOwnerId() );
        documentResponse.updatedAt( document.getUpdatedAt() );

        documentResponse.status( document.getStatus().name() );

        return documentResponse.build();
    }
}
