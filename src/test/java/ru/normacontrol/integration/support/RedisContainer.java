package ru.normacontrol.integration.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Minimal Redis container wrapper used by integration tests.
 */
public class RedisContainer extends GenericContainer<RedisContainer> {

    private static final int REDIS_PORT = 6379;

    public RedisContainer() {
        super(DockerImageName.parse("redis:7-alpine"));
        withExposedPorts(REDIS_PORT);
    }

    public String getRedisHost() {
        return getHost();
    }

    public Integer getRedisPort() {
        return getMappedPort(REDIS_PORT);
    }
}
