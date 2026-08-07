package com.flowgate.routing;

import com.flowgate.loadbalance.LeastConnectionsStrategy;
import com.flowgate.loadbalance.RoundRobinStrategy;
import com.flowgate.ratelimit.RateLimiter;
import com.flowgate.resilience.RouteCircuitBreakers;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class GatewayRoutes {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(
            WebClient webClient, RouteMatcher routeMatcher,
            RateLimiter rateLimiter, RouteCircuitBreakers circuitBreakers,
            HealthChecker healthChecker, RoundRobinStrategy roundRobin,
            LeastConnectionsStrategy leastConn) {

        return RouterFunctions.route(all(), request ->
                routeMatcher.match(request.path())
                        .map(route -> rateLimiter.checkLimit(clientId(request), route.id())
                                .flatMap(result -> {
                                    if (!result.allowed()) {
                                        long retryAfter = rateLimiter.estimateRetryAfterSeconds(result.tokensRemaining());
                                        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                                .header("Retry-After", String.valueOf(retryAfter))
                                                .bodyValue("Rate limit exceeded. Retry after " + retryAfter + "s.");
                                    }
                                    return forward(webClient, request, route, circuitBreakers,
                                            healthChecker, roundRobin, leastConn);
                                }))
                        .orElseGet(() -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                .bodyValue("No route matched: " + request.path()))
        );
    }

    private String clientId(ServerRequest request) {
        return request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
    }

    private String selectTarget(RouteDefinition route, List<String> healthyInstances,
                                RoundRobinStrategy roundRobin, LeastConnectionsStrategy leastConn) {
        return "least-connections".equals(route.loadBalancingStrategy())
                ? leastConn.selectInstance(route.id(), healthyInstances)
                : roundRobin.selectInstance(route.id(), healthyInstances); // default
    }

    private Mono<ServerResponse> forward(
            WebClient webClient, ServerRequest request, RouteDefinition route,
            RouteCircuitBreakers circuitBreakers, HealthChecker healthChecker,
            RoundRobinStrategy roundRobin, LeastConnectionsStrategy leastConn) {

        List<String> healthyInstances = healthChecker.getHealthyInstances(route);
        String target = selectTarget(route, healthyInstances, roundRobin, leastConn);
        String targetUrl = target + request.path();

        Mono<ServerResponse> call = webClient
                .method(request.method())
                .uri(targetUrl)
                .headers(headers -> headers.addAll(request.headers().asHttpHeaders()))
                .body(request.bodyToMono(byte[].class), byte[].class)
                .exchangeToMono(clientResponse ->
                        ServerResponse
                                .status(clientResponse.statusCode())
                                .headers(headers -> headers.addAll(clientResponse.headers().asHttpHeaders()))
                                .body(clientResponse.bodyToMono(byte[].class), byte[].class)
                );

        return call
                .transformDeferred(RetryOperator.of(circuitBreakers.retryForRoute(route.id())))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakers.forRoute(route.id())))
                .onErrorResume(CallNotPermittedException.class, ex ->
                        ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .bodyValue("Service temporarily unavailable (circuit open) for route: " + route.id())
                );
    }
}