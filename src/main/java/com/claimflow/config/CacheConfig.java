package com.claimflow.config;

import com.claimflow.policy.PolicySnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String POLICIES = "policies";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    RedisCacheManagerBuilderCustomizer cacheCustomizer() {
        // Typed JSON serializer: readable in redis-cli, and no class names stored in the cache.
        var serializer = new Jackson2JsonRedisSerializer<>(new ObjectMapper(), PolicySnapshot.class);
        RedisCacheConfiguration policies = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .prefixCacheNameWith("claimflow:")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
        return builder -> builder.withCacheConfiguration(POLICIES, policies);
    }

    /** Redis is an optimisation, not a dependency: if it is down, fall through to the database. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed for {}::{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}::{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("Cache evict failed for {}::{} - entry may be stale until TTL: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {} - {}", cache.getName(), e.getMessage());
            }
        };
    }
}
