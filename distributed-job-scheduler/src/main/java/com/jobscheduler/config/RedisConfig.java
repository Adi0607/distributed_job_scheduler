package com.jobscheduler.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration providing both Spring RedisTemplate and
 * Lettuce's native RedisCommands for the distributed lock implementation.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Native Lettuce client used by RedisDistributedLock for SET NX EX commands
     * and Lua script execution.
     *
     * @return a stateful Redis connection
     */
    @Bean
    public StatefulRedisConnection<String, String> lettuceConnection() {
        RedisURI uri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        RedisClient client = RedisClient.create(uri);
        return client.connect();
    }

    /**
     * Synchronous Redis commands API used by the distributed lock.
     *
     * @param connection the stateful Lettuce connection
     * @return synchronous commands interface
     */
    @Bean
    public RedisCommands<String, String> redisCommands(StatefulRedisConnection<String, String> connection) {
        return connection.sync();
    }

    /**
     * Spring RedisTemplate with String serializers for general-purpose use.
     *
     * @param connectionFactory Spring-managed connection factory
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}
