package ru.normacontrol.infrastructure.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Конфигурация Kafka-топиков.
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.document-check}")
    private String documentCheckTopic;

    @Value("${kafka.topic.check-result}")
    private String checkResultTopic;

    @Bean
    public NewTopic documentCheckTopic() {
        return TopicBuilder.name(documentCheckTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic checkResultTopic() {
        return TopicBuilder.name(checkResultTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
