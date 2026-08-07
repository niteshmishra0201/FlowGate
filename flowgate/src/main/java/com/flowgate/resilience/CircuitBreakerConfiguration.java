package com.flowgate.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                          // trip if 50% of calls fail
                .slowCallRateThreshold(50)                          // also trip if 50% are "slow"
                .slowCallDurationThreshold(Duration.ofSeconds(2))   // "slow" = over 2s
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)                              // based on the last 10 calls
                .minimumNumberOfCalls(5)                            // need at least 5 calls before evaluating
                .waitDurationInOpenState(Duration.ofSeconds(10))    // cooldown before half-open
                .permittedNumberOfCallsInHalfOpenState(3)           // 3 test calls in half-open
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)                              // original call + 2 retries
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialRandomBackoff(
                                Duration.ofMillis(200),        // initial wait
                                2.0,                            // multiplier (200ms -> 400ms -> 800ms)
                                0.5                              // jitter factor: randomize +/-50% of the interval
                        ))
                .build();
        return RetryRegistry.of(config);
    }
}