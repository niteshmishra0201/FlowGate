package com.flowgate.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ResponseCache {

    public record CachedResponse(int statusCode, byte[] body) {}

    private final Cache<String, CachedResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))   // TTL: 30s, justified below
            .maximumSize(10_000)                          // bound memory usage
            .build();

    public String buildKey(String method, String path, String clientId) {
        return method + ":" + path + ":" + clientId;
    }

    public CachedResponse get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, CachedResponse response) {
        cache.put(key, response);
    }
}