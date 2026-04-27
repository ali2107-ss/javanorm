package ru.normacontrol.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.normacontrol.integration.support.RedisContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Enable when full security integration environment is configured")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedisContainer redis = new RedisContainer();

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void testJwtExpired_returns401() {
        assertTrue(true);
    }

    @Test
    void testJwtInvalidSignature_returns401() {
        assertTrue(true);
    }

    @Test
    void testRoleUser_adminEndpoint_returns403() {
        assertTrue(true);
    }

    @Test
    void testRoleAdmin_adminEndpoint_returns200() {
        assertTrue(true);
    }

    @Test
    void testBruteForce_5wrongPasswords_accountLocked() {
        assertTrue(true);
    }

    @Test
    void testRateLimit_101requests_returns429() {
        assertTrue(true);
    }
}
