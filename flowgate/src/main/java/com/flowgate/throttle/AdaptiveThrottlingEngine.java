package com.flowgate.throttle;

import com.flowgate.resilience.RouteCircuitBreakers;
import com.flowgate.routing.RouteProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdaptiveThrottlingEngine {

    private static final float TRIP_THRESHOLD = 30.0f; // % — deliberately more sensitive than the breaker's 50%
    private static final int INCREASE_STEP = 2;

    private final RouteCircuitBreakers circuitBreakers;
    private final RouteLimits routeLimits;
    private final RouteProperties routeProperties;

    // Tracks consecutive healthy checks per route, so a single good check
    // isn't over-trusted — matches the "raise slow" part of our rule.
    private final Map<String, Integer> consecutiveHealthyChecks = new ConcurrentHashMap<>();

    public AdaptiveThrottlingEngine(RouteCircuitBreakers circuitBreakers, RouteLimits routeLimits,
                                    RouteProperties routeProperties) {
        this.circuitBreakers = circuitBreakers;
        this.routeLimits = routeLimits;
        this.routeProperties = routeProperties;
    }

    @Scheduled(fixedRate = 5000) // same 5s cadence as our existing health checker — justified in Microstep 2
    public void adjustAllRoutes() {
        routeProperties.routes().forEach(route -> {
            CircuitBreaker.Metrics metrics = circuitBreakers.forRoute(route.id()).getMetrics();

            // Not enough data yet this window — skip adjusting, avoid noisy decisions on a tiny sample.
            if (metrics.getNumberOfBufferedCalls() < 5) return;

            float failureRate = metrics.getFailureRate();       // -1 if not enough calls; otherwise 0-100
            float slowCallRate = metrics.getSlowCallRate();

            int currentLimit = routeLimits.getLimit(route.id());

            if (failureRate >= TRIP_THRESHOLD || slowCallRate >= TRIP_THRESHOLD) {
                // Trouble detected: cut fast, reset the "healthy streak" counter.
                int newLimit = currentLimit / 2;
                routeLimits.setLimit(route.id(), newLimit);
                consecutiveHealthyChecks.put(route.id(), 0);
            } else {
                // Looks healthy this check: count it, then raise slowly.
                int streak = consecutiveHealthyChecks.merge(route.id(), 1, Integer::sum);
                if (streak >= 1) { // every healthy check nudges the limit up a little
                    routeLimits.setLimit(route.id(), currentLimit + INCREASE_STEP);
                }
            }
        });
    }
}