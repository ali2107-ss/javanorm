package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixResponse {

    private String fixedDocumentKey;
    private String fixedDocumentUrl;
    private int fixedCount;
    private List<String> manualActions;
    private String message;
}
