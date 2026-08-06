package com.flowgate.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RateLimiter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> script;

    private static final int CAPACITY = 10;       // max tokens (burst size)
    private static final double REFILL_RATE = 10.0 / 60.0; // 10 tokens per 60s

    public RateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
    }

    public Mono<RateLimitResult> checkLimit(String clientId) {
        String key = "rate_limit:" + clientId;
        double now = System.currentTimeMillis() / 1000.0;

        return redisTemplate.execute(
                        script,
                        List.of(key),
                        List.of(String.valueOf(CAPACITY), String.valueOf(REFILL_RATE), String.valueOf(now))
                )
                .next()
                .map(result -> {
                    List<Long> values = (List<Long>) result;
                    boolean allowed = values.get(0) == 1;
                    long tokensRemaining = values.get(1);
                    return new RateLimitResult(allowed, tokensRemaining);
                });
    }

    public long estimateRetryAfterSeconds(long tokensRemaining) {
        // tokensRemaining will be 0 when rejected; estimate time until 1 token is available
        double secondsPerToken = 1.0 / REFILL_RATE;
        return Math.max(1, (long) Math.ceil(secondsPerToken));
    }
}