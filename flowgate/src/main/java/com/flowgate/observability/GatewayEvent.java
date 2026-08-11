package com.flowgate.observability;

import java.time.Instant;

public record GatewayEvent(
        String type,        // "request_completed" | "rate_limit_rejected" | "circuit_breaker_transition"
        String routeId,
        Integer status,      // nullable — not relevant for circuit breaker events
        Boolean cacheHit,    // nullable — not relevant for rejection/breaker events
        String detail,       // free-text extra info, e.g. breaker "CLOSED -> OPEN"
        Instant timestamp
) {
    public static GatewayEvent requestCompleted(String routeId, int status, boolean cacheHit) {
        return new GatewayEvent("request_completed", routeId, status, cacheHit, null, Instant.now());
    }

    public static GatewayEvent rateLimitRejected(String routeId) {
        return new GatewayEvent("rate_limit_rejected", routeId, null, null, null, Instant.now());
    }

    public static GatewayEvent circuitBreakerTransition(String routeId, String detail) {
        return new GatewayEvent("circuit_breaker_transition", routeId, null, null, detail, Instant.now());
    }

    public static GatewayEvent anomalyDetected(String routeId, String detail) {
        return new GatewayEvent("anomaly_detected", routeId, null, null, detail, Instant.now());
    }
}