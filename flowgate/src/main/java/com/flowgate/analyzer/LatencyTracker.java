package com.flowgate.analyzer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class LatencyTracker {

    private static final int WINDOW_SIZE = 50; // rolling sample count per route

    // One bounded deque of recent latencies (in ms) per route.
    private final Map<String, ConcurrentLinkedDeque<Long>> samples = new ConcurrentHashMap<>();

    public void recordLatency(String routeId, long latencyMs) {
        ConcurrentLinkedDeque<Long> deque = samples.computeIfAbsent(routeId, id -> new ConcurrentLinkedDeque<>());
        deque.addLast(latencyMs);
        while (deque.size() > WINDOW_SIZE) {
            deque.pollFirst(); // drop oldest once we exceed the window
        }
    }

    public Stats computeStats(String routeId) {
        ConcurrentLinkedDeque<Long> deque = samples.getOrDefault(routeId, new ConcurrentLinkedDeque<>());
        if (deque.size() < 10) {
            return null; // not enough data yet — same "don't trust a tiny sample" guard as Phase 3/4
        }

        double mean = deque.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = deque.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return new Stats(mean, stdDev);
    }

    public record Stats(double mean, double stdDev) {
        public double zScoreFor(long value) {
            if (stdDev == 0) return 0; // avoid divide-by-zero when all samples are identical
            return (value - mean) / stdDev;
        }
    }
}