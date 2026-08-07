package com.flowgate.resilience;

import com.flowgate.routing.RouteProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

@Component
public class RouteCircuitBreakers {

    private final CircuitBreakerRegistry cbRegistry;
    private final RetryRegistry retryRegistry;

    public RouteCircuitBreakers(CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry,
                                RouteProperties routeProperties) {
        this.cbRegistry = cbRegistry;
        this.retryRegistry = retryRegistry;
        routeProperties.routes().forEach(route -> {
            cbRegistry.circuitBreaker(route.id());
            retryRegistry.retry(route.id());
        });
    }

    public CircuitBreaker forRoute(String routeId) {
        return cbRegistry.circuitBreaker(routeId);
    }

    public Retry retryForRoute(String routeId) {
        return retryRegistry.retry(routeId);
    }
}