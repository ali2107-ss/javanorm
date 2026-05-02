package ru.normacontrol.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO — ответ на запрос авто-исправления документа.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixResponse {

    /** Ключ объекта в MinIO для исправленного файла. */
    private String fixedDocumentKey;

    /** Публичный URL (или путь) для скачивания. */
    private String fixedDocumentUrl;

    /** Количество автоматически применённых исправлений. */
    private int fixedCount;

    /** Информационное сообщение о результате. */
    private String message;
}
