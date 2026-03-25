package com.jobscheduler.lock;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.ScriptOutputType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis-based distributed lock implementation using Lettuce.
 *
 * <p>Uses SET NX EX (set if not exists with expiry) for atomic lock acquisition.
 * Each application instance has a unique ID stored as the lock value, ensuring that
 * only the instance that acquired the lock can release it.
 *
 * <p>Release uses a Lua script for atomic check-and-delete, preventing accidental
 * release of another instance's lock in distributed deployments.
 */
@Slf4j
@Component
public class RedisDistributedLock {

    /** Lua script: atomically release lock only if value matches this instance's ID. */
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final RedisCommands<String, String> redisCommands;

    /** Unique identifier for this application instance. Used as the lock value. */
    private final String instanceId;

    public RedisDistributedLock(RedisCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
        this.instanceId = UUID.randomUUID().toString();
        log.info("RedisDistributedLock initialized with instanceId={}", instanceId);
    }

    /**
     * Attempts to acquire a distributed lock with the given key and TTL.
     *
     * @param key        the lock key (e.g., "job-lock:{jobId}")
     * @param ttlSeconds how long the lock should be held before auto-expiry
     * @return true if the lock was acquired, false if already held by another instance
     */
    public boolean tryAcquire(String key, int ttlSeconds) {
        log.debug("Attempting to acquire lock: key={}, ttl={}s, instanceId={}", key, ttlSeconds, instanceId);
        String result = redisCommands.set(key, instanceId, SetArgs.Builder.nx().ex(ttlSeconds));
        boolean acquired = "OK".equals(result);
        if (acquired) {
            log.debug("Lock acquired: key={}, instanceId={}", key, instanceId);
        } else {
            log.debug("Lock NOT acquired (held by another instance): key={}", key);
        }
        return acquired;
    }

    /**
     * Releases the lock identified by the given key.
     * Uses a Lua script to atomically verify the lock belongs to this instance
     * before deleting it, preventing accidental release of another instance's lock.
     *
     * @param key the lock key to release
     */
    public void release(String key) {
        log.debug("Releasing lock: key={}, instanceId={}", key, instanceId);
        Long result = redisCommands.eval(RELEASE_SCRIPT, ScriptOutputType.INTEGER,
                new String[]{key}, instanceId);
        if (result != null && result == 1L) {
            log.debug("Lock released successfully: key={}", key);
        } else {
            log.warn("Lock release skipped — key not found or held by another instance: key={}", key);
        }
    }

    /**
     * Returns the unique instance ID used as the lock value for this JVM.
     *
     * @return the instance ID UUID string
     */
    public String getInstanceId() {
        return instanceId;
    }
}
