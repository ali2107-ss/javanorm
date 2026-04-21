package ru.normacontrol.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Конфигурация Kafka-топиков.
 * Настройки подключения и JSON-сериализаторы уже заданы в application.yml.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic checkRequestedTopic() {
        return TopicBuilder.name("normacontrol.check.requested")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic checkCompletedTopic() {
        return TopicBuilder.name("normacontrol.check.completed")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic checkDltTopic() {
        // По умолчанию Spring @RetryableTopic добавляет суффикс -dlt
        return TopicBuilder.name("normacontrol.check.requested-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
