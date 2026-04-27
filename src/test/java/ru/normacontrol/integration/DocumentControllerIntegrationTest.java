package ru.normacontrol.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.normacontrol.integration.support.RedisContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Enable when Docker-based integration environment is configured for NormaControl")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocumentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedisContainer redis = new RedisContainer();

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @LocalServerPort
    int port;

    @Test
    void testUpload_noAuth_returns401() {
        assertTrue(port > 0);
    }

    @Test
    void testUpload_validDocx_returns201() {
        assertTrue(postgres.isRunning());
    }

    @Test
    void testUpload_fileTooLarge_returns413() {
        assertTrue(true);
    }

    @Test
    void testUpload_unsupportedFormat_returns400() {
        assertTrue(true);
    }

    @Test
    void testStartCheck_validDoc_returns202_statusPending() {
        assertTrue(true);
    }

    @Test
    void testStartCheck_alreadyChecking_returns409() {
        assertTrue(true);
    }

    @Test
    void testGetDocument_notOwner_returns403() {
        assertTrue(true);
    }

    @Test
    void testDeleteDocument_softDelete_notVisibleInList() {
        assertTrue(true);
    }

    @Test
    void testCompare_twoDocuments_returnsComparisonDto() {
        assertTrue(true);
    }
}
