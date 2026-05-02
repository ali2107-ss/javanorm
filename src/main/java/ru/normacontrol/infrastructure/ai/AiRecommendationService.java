package ru.normacontrol.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import ru.normacontrol.domain.entity.Violation;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiRecommendationService {

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final Bucket bucket;
    private final ObjectMapper objectMapper;

    @Value("${ai.anthropic.api-key:default_key}")
    private String apiKey;

    public AiRecommendationService(
            WebClient.Builder webClientBuilder,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://api.anthropic.com/v1").build();
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;

        // Rate limit: не более 10 AI-запросов в минуту
        Bandwidth limit = Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    public CompletableFuture<String> generateRecommendation(Violation violation, String documentFragment) {
        String ruleCode = violation.getRuleCode();
        String description = violation.getDescription();

        // Кэш в Redis: ключ = MD5(ruleCode + fragment)
        String rawKey = ruleCode + ":" + documentFragment;
        String cacheKey = "ai:recommendation:" + DigestUtils.md5DigestAsHex(rawKey.getBytes());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    meterRegistry.counter("ai.recommendations.cache.hit").increment();
                    return cached;
                }

                // Check rate limit
                if (!bucket.tryConsume(1)) {
                    log.warn("Rate limit exceeded for AI recommendations. Using fallback.");
                    return violation.getSuggestion();
                }

                String prompt = String.format(
                        "Ты эксперт по ГОСТ 19.201-78. Найдено нарушение: %s. Описание: %s. Фрагмент: %s. Дай конкретную рекомендацию на русском (2-3 предложения) как именно исправить это нарушение.",
                        ruleCode, description, documentFragment != null ? documentFragment : "не указан"
                );

                Map<String, Object> requestBody = Map.of(
                        "model", "claude-3-haiku-20240307",
                        "max_tokens", 200,
                        "messages", new Object[]{
                                Map.of("role", "user", "content", prompt)
                        }
                );

                Timer.Sample sample = Timer.start(meterRegistry);

                String responseStr = webClient.post()
                        .uri("/messages")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(5))
                        .block();

                sample.stop(meterRegistry.timer("ai.recommendations.latency"));

                JsonNode root = objectMapper.readTree(responseStr);
                String recommendation = root.path("content").get(0).path("text").asText();

                // Save to cache with TTL = 24 hours
                redisTemplate.opsForValue().set(cacheKey, recommendation, 24, TimeUnit.HOURS);
                meterRegistry.counter("ai.recommendations.generated").increment();

                return recommendation;

            } catch (Exception e) {
                log.error("Failed to generate AI recommendation: {}", e.getMessage());
                return violation.getSuggestion();
            }
        });
    }
}
