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
    private final AnomalyNarrator narrator;

    public AnomalyDetector(LatencyTracker latencyTracker, GatewayEventBus eventBus, AnomalyNarrator narrator) {
        this.latencyTracker = latencyTracker;
        this.eventBus = eventBus;
        this.narrator = narrator;
    }

    public void checkForAnomaly(String routeId, long latencyMs) {

        latencyTracker.recordLatency(routeId, latencyMs);

        LatencyTracker.Stats stats = latencyTracker.computeStats(routeId);
        if (stats == null) return; // not enough history yet

        double zScore = stats.zScoreFor(latencyMs);
        if (Math.abs(zScore) >= Z_SCORE_THRESHOLD) {
            String detail = String.format(
                    "Latency %dms is %.1f standard deviations from route average (%.0fms)",
                    latencyMs, zScore, stats.mean());
            log.warn("Anomaly detected for route={}: {}", routeId, detail);
            narrator.narrate(routeId, detail)
                    .subscribe(narratedText ->
                            eventBus.publish(com.flowgate.observability.GatewayEvent.anomalyDetected(routeId, narratedText))
                    );
        }
    }
}