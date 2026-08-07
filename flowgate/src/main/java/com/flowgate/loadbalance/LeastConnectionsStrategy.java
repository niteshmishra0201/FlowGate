package com.flowgate.loadbalance;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LeastConnectionsStrategy implements LoadBalancingStrategy {

    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    @Override
    public String selectInstance(String routeId, List<String> healthyInstances) {
        return healthyInstances.stream()
                .min(Comparator.comparingInt(uri -> activeConnections.getOrDefault(uri, new AtomicInteger(0)).get()))
                .orElseThrow();
    }

    public void incrementConnections(String uri) {
        activeConnections.computeIfAbsent(uri, u -> new AtomicInteger(0)).incrementAndGet();
    }

    public void decrementConnections(String uri) {
        activeConnections.computeIfAbsent(uri, u -> new AtomicInteger(0)).decrementAndGet();
    }
}