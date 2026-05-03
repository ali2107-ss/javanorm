package ru.normacontrol.infrastructure.plagiarism;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_hashes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTextHashJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "sentence_hash", nullable = false, length = 64)
    private String sentenceHash;

    @Column(name = "sentence_preview", length = 100)
    private String sentencePreview;

    @Column(name = "sentence_index")
    private Integer sentenceIndex;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
