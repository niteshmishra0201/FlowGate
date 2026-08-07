package com.flowgate.routing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class HealthChecker {

    private final WebClient webClient;
    private final RouteProperties routeProperties;

    // Now keyed by individual instance URI, not route id.
    private final Map<String, Boolean> instanceHealth = new ConcurrentHashMap<>();

    public HealthChecker(WebClient webClient, RouteProperties routeProperties) {
        this.webClient = webClient;
        this.routeProperties = routeProperties;
        routeProperties.routes().forEach(route ->
                route.targetUris().forEach(uri -> instanceHealth.put(uri, true))
        );
    }

    @Scheduled(fixedRate = 5000)
    public void checkAll() {
        Flux.fromIterable(routeProperties.routes())
                .flatMap(route -> Flux.fromIterable(route.targetUris())
                        .flatMap(uri ->
                                webClient.get()
                                        .uri(uri + route.healthCheckPath())
                                        .exchangeToMono(response -> Mono.just(response.statusCode().is2xxSuccessful()))
                                        .timeout(Duration.ofSeconds(2))
                                        .onErrorReturn(false)
                                        .doOnNext(healthy -> instanceHealth.put(uri, healthy))
                        )
                )
                .subscribe();
    }

    public boolean isInstanceHealthy(String uri) {
        return instanceHealth.getOrDefault(uri, true);
    }

    // NEW: what the registry/load-balancer will actually query.
    public List<String> getHealthyInstances(RouteDefinition route) {
        return route.targetUris().stream()
                .filter(this::isInstanceHealthy)
                .collect(Collectors.toList());
    }
}