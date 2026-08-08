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
                                RouteProperties routeProperties, com.flowgate.observability.GatewayEventBus eventBus) {
        this.cbRegistry = cbRegistry;
        this.retryRegistry = retryRegistry;

        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RouteCircuitBreakers.class);

        routeProperties.routes().forEach(route -> {
            io.github.resilience4j.circuitbreaker.CircuitBreaker cb = cbRegistry.circuitBreaker(route.id());
            cb.getEventPublisher().onStateTransition(event -> {
                String detail = event.getStateTransition().getFromState() + " -> " + event.getStateTransition().getToState();
                log.warn("Circuit breaker for route={} transitioned {}", route.id(), detail);
                eventBus.publish(com.flowgate.observability.GatewayEvent.circuitBreakerTransition(route.id(), detail));
            });
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