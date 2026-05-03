package ru.normacontrol.infrastructure.plagiarism;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentTextHashJpaRepository extends JpaRepository<DocumentTextHashJpaEntity, UUID> {

    @Query("SELECT h FROM DocumentTextHashJpaEntity h WHERE h.sentenceHash IN :hashes AND h.documentId != :currentDocumentId")
    List<DocumentTextHashJpaEntity> findMatches(@Param("hashes") List<String> hashes, @Param("currentDocumentId") UUID currentDocumentId);
    
    void deleteByDocumentId(UUID documentId);
}
