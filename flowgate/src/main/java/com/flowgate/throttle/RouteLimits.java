package com.flowgate.throttle;

import com.flowgate.routing.RouteProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RouteLimits {

    public static final int MIN_LIMIT = 2;
    public static final int MAX_LIMIT = 10;

    private final Map<String, Integer> currentLimits = new ConcurrentHashMap<>();

    public RouteLimits(RouteProperties routeProperties) {
        // Every route starts at the healthy maximum.
        routeProperties.routes().forEach(route -> currentLimits.put(route.id(), MAX_LIMIT));
    }

    public int getLimit(String routeId) {
        return currentLimits.getOrDefault(routeId, MAX_LIMIT);
    }

    public void setLimit(String routeId, int newLimit) {
        int clamped = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, newLimit));
        currentLimits.put(routeId, clamped);
    }
}