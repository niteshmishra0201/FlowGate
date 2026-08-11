package com.flowgate.analyzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class AnomalyNarrator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnomalyNarrator.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    private final WebClient webClient;
    private final String apiKey;
    private final boolean enabled;

    public AnomalyNarrator(@Value("${flowgate.gemini.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.webClient = WebClient.builder().build();
    }

    public Mono<String> narrate(String routeId, String rawDetail) {
        if (!enabled) {
            return Mono.just(rawDetail); // fall back to raw detail if no API key configured
        }

        String prompt = "You are monitoring an API gateway. Write ONE short, plain-English sentence " +
                "(no preamble) explaining this detected traffic anomaly for a dashboard alert. " +
                "Route: " + routeId + ". Raw detail: " + rawDetail;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        return webClient.post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractText)
                .timeout(java.time.Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Gemini narration failed, falling back to raw detail: {}", e.getMessage());
                    return Mono.just(rawDetail); // graceful fallback — never let this break the flow
                });
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            var candidates = (List<Map<String, Object>>) response.get("candidates");
            var content = (Map<String, Object>) candidates.get(0).get("content");
            var parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            return "Anomaly detected (narration unavailable)";
        }
    }
}