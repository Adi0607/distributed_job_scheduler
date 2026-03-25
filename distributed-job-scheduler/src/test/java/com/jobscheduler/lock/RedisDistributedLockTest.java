package com.jobscheduler.lock;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.embedded.RedisServer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RedisDistributedLock using embedded Redis.
 */
class RedisDistributedLockTest {

    private static RedisServer embeddedRedis;
    private static RedisCommands<String, String> redisCommands;
    private static StatefulRedisConnection<String, String> connection;

    private RedisDistributedLock lock;

    @BeforeAll
    static void startEmbeddedRedis() throws IOException {
        embeddedRedis = new RedisServer(16379);
        embeddedRedis.start();

        RedisClient client = RedisClient.create(
                RedisURI.builder().withHost("localhost").withPort(16379).build());
        connection = client.connect();
        redisCommands = connection.sync();
    }

    @AfterAll
    static void stopEmbeddedRedis() throws IOException {
        connection.close();
        embeddedRedis.stop();
    }

    @BeforeEach
    void setUp() {
        lock = new RedisDistributedLock(redisCommands);
        // Clean up any leftover keys
        redisCommands.flushall();
    }

    /**
     * The same key cannot be acquired twice by the same instance.
     */
    @Test
    void sameKey_cannotBeAcquiredTwice() {
        boolean first = lock.tryAcquire("test-lock:job-1", 60);
        boolean second = lock.tryAcquire("test-lock:job-1", 60);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    /**
     * After releasing, the key can be acquired again.
     */
    @Test
    void lock_isReleasedCorrectly() {
        boolean acquired = lock.tryAcquire("test-lock:job-2", 60);
        assertThat(acquired).isTrue();

        lock.release("test-lock:job-2");

        boolean reacquired = lock.tryAcquire("test-lock:job-2", 60);
        assertThat(reacquired).isTrue();
    }

    /**
     * Lua script prevents a different-instance lock from being released by this instance.
     * We simulate another instance by writing a different instanceId directly to Redis.
     */
    @Test
    void luaScript_preventsWrongInstanceFromReleasingLock() {
        // Simulate another instance holding the lock with their own instanceId
        redisCommands.set("test-lock:job-3", "other-instance-id");

        // Our lock tries to release — Lua script should prevent it
        lock.release("test-lock:job-3");

        // Key must still exist (not deleted by our release)
        String value = redisCommands.get("test-lock:job-3");
        assertThat(value).isEqualTo("other-instance-id");
    }

    /**
     * Different keys can be independently acquired.
     */
    @Test
    void differentKeys_canBeAcquiredIndependently() {
        boolean first = lock.tryAcquire("test-lock:job-4a", 60);
        boolean second = lock.tryAcquire("test-lock:job-4b", 60);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }
}
