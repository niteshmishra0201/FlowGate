package com.flowgate.loadbalance;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoundRobinStrategy implements LoadBalancingStrategy {

    // One counter per route, so each route cycles independently.
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public String selectInstance(String routeId, List<String> healthyInstances) {
        AtomicInteger counter = counters.computeIfAbsent(routeId, id -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % healthyInstances.size();
        return healthyInstances.get(index);
    }
}