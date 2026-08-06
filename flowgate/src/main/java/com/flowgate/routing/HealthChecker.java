package com.flowgate.routing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HealthChecker {

    private final WebClient webClient;
    private final RouteProperties routeProperties;
    private final Map<String, Boolean> routeHealth = new ConcurrentHashMap<>();

    public HealthChecker(WebClient webClient, RouteProperties routeProperties) {
        this.webClient = webClient;
        this.routeProperties = routeProperties;
        // Assume healthy until first check completes, so we don't
        // block routing decisions before the first poll has even run.
        routeProperties.routes().forEach(r -> routeHealth.put(r.id(), true));
    }

    @Scheduled(fixedRate = 5000) // every 5 seconds
    public void checkAll() {
        Flux.fromIterable(routeProperties.routes())
                .flatMap(route ->
                        webClient.get()
                                .uri(route.targetUri() + route.healthCheckPath())
                                .exchangeToMono(response -> reactor.core.publisher.Mono.just(response.statusCode().is2xxSuccessful()))
                                .timeout(Duration.ofSeconds(2))
                                .onErrorReturn(false) // any error (timeout, refused, etc.) = unhealthy
                                .doOnNext(healthy -> routeHealth.put(route.id(), healthy))
                )
                .subscribe();
    }

    public boolean isHealthy(String routeId) {
        return routeHealth.getOrDefault(routeId, true);
    }
}