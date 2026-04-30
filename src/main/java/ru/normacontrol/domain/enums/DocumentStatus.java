package ru.normacontrol.domain.enums;

/**
 * Статусы жизненного цикла документа.
 */
public enum DocumentStatus {
    UPLOADED,
    QUEUED,
    CHECKING,
    CHECKED,
    FAILED,
    DELETED
}
