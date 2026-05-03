package ru.normacontrol.infrastructure.plagiarism;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.domain.repository.ReadDocumentRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlagiarismChecker {

    private final DocumentTextHashJpaRepository hashRepository;
    private final ReadDocumentRepository documentRepository;

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?]+[.!?]+");

    public PlagiarismResult check(String documentText, UUID currentDocumentId) {
        if (documentText == null || documentText.isBlank()) {
            return new PlagiarismResult(100, 0, "УНИКАЛЬНЫЙ", List.of(), List.of());
        }

        List<String> sentences = extractSentences(documentText);
        if (sentences.isEmpty()) {
            return new PlagiarismResult(100, 0, "УНИКАЛЬНЫЙ", List.of(), List.of());
        }

        Map<String, String> hashToSentence = sentences.stream()
                .filter(s -> s.length() > 20) // игнорируем слишком короткие предложения
                .collect(Collectors.toMap(
                        this::hashSentence,
                        s -> s,
                        (s1, s2) -> s1 // при коллизии хэшей (хотя для SHA-256 вряд ли)
                ));

        if (hashToSentence.isEmpty()) {
            return new PlagiarismResult(100, 0, "УНИКАЛЬНЫЙ", List.of(), List.of());
        }

        List<String> queryHashes = new ArrayList<>(hashToSentence.keySet());
        List<DocumentTextHashJpaEntity> matches = hashRepository.findMatches(queryHashes, currentDocumentId);

        if (matches.isEmpty()) {
            return new PlagiarismResult(100, 0, "УНИКАЛЬНЫЙ", List.of(), List.of());
        }

        int totalSentences = hashToSentence.size();
        
        // Группируем совпадения по документам
        Map<UUID, List<DocumentTextHashJpaEntity>> matchesByDoc = matches.stream()
                .collect(Collectors.groupingBy(DocumentTextHashJpaEntity::getDocumentId));

        List<SimilarFragment> suspiciousFragments = new ArrayList<>();
        Set<UUID> similarDocIds = matchesByDoc.keySet();

        int matchedSentences = 0;
        for (DocumentTextHashJpaEntity match : matches) {
            String originalText = hashToSentence.get(match.getSentenceHash());
            if (originalText != null) {
                matchedSentences++;
                // Для простоты каждое совпадение добавляем как фрагмент (можно объединять соседние)
                String sourceName = documentRepository.findById(match.getDocumentId())
                        .map(ru.normacontrol.domain.entity.Document::getOriginalFilename)
                        .orElse("Неизвестный документ");
                
                suspiciousFragments.add(new SimilarFragment(
                        originalText,
                        match.getSentencePreview() + "...",
                        match.getDocumentId(),
                        sourceName,
                        100 // Точное совпадение хэша = 100%
                ));
                // Удаляем из мапы чтобы не считать дважды если есть дубли в других доках
                hashToSentence.remove(match.getSentenceHash());
            }
        }

        int plagiarismPercent = (int) Math.round((double) matchedSentences / totalSentences * 100);
        int uniquenessPercent = 100 - plagiarismPercent;

        String verdict = uniquenessPercent >= 85 ? "УНИКАЛЬНЫЙ" 
                       : uniquenessPercent >= 70 ? "ЧАСТИЧНО УНИКАЛЬНЫЙ" 
                       : "ПЛАГИАТ";

        return new PlagiarismResult(
                uniquenessPercent,
                plagiarismPercent,
                verdict,
                suspiciousFragments,
                new ArrayList<>(similarDocIds)
        );
    }

    @Transactional
    public void saveHashes(UUID documentId, String documentText) {
        if (documentText == null || documentText.isBlank()) return;
        
        // Удаляем старые хэши для этого документа если есть
        hashRepository.deleteByDocumentId(documentId);

        List<String> sentences = extractSentences(documentText);
        List<DocumentTextHashJpaEntity> entities = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (sentence.length() <= 20) continue;

            String hash = hashSentence(sentence);
            String preview = sentence.substring(0, Math.min(sentence.length(), 100));

            entities.add(DocumentTextHashJpaEntity.builder()
                    .id(UUID.randomUUID())
                    .documentId(documentId)
                    .sentenceHash(hash)
                    .sentencePreview(preview)
                    .sentenceIndex(i)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        if (!entities.isEmpty()) {
            hashRepository.saveAll(entities);
            log.info("Saved {} sentence hashes for document {}", entities.size(), documentId);
        }
    }

    private List<String> extractSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // Простая очистка от лишних переносов строк и пробелов
        String cleanText = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        Matcher matcher = SENTENCE_PATTERN.matcher(cleanText);
        while (matcher.find()) {
            sentences.add(matcher.group().trim());
        }
        return sentences;
    }

    private String hashSentence(String sentence) {
        try {
            // Нормализуем строку: нижний регистр, убираем знаки препинания и пробелы
            String normalized = sentence.toLowerCase().replaceAll("[^a-zа-я0-9]", "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not found", e);
        }
    }
}
