package com.flowgate.analyzer;

import com.flowgate.observability.GatewayEventBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnomalyDetector {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnomalyDetector.class);
    private static final double Z_SCORE_THRESHOLD = 3.0; // justified below

    private final LatencyTracker latencyTracker;
    private final GatewayEventBus eventBus;

    public AnomalyDetector(LatencyTracker latencyTracker, GatewayEventBus eventBus) {
        this.latencyTracker = latencyTracker;
        this.eventBus = eventBus;
    }

    public void checkForAnomaly(String routeId, long latencyMs) {
        // Record first, so this very request contributes to future baseline data too.
        latencyTracker.recordLatency(routeId, latencyMs);

        LatencyTracker.Stats stats = latencyTracker.computeStats(routeId);
        if (stats == null) return; // not enough history yet

        double zScore = stats.zScoreFor(latencyMs);
        if (Math.abs(zScore) >= Z_SCORE_THRESHOLD) {
            String detail = String.format(
                    "Latency %dms is %.1f standard deviations from route average (%.0fms)",
                    latencyMs, zScore, stats.mean());
            log.warn("Anomaly detected for route={}: {}", routeId, detail);
            eventBus.publish(com.flowgate.observability.GatewayEvent.circuitBreakerTransition(
                    // Reusing the existing event shape for now — repurposed as a generic "notable event" carrier.
                    // We'll give this its own proper event type in the next microstep.
                    routeId, "ANOMALY: " + detail
            ));
        }
    }
}