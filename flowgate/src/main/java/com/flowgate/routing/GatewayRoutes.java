package com.flowgate.routing;

import com.flowgate.auth.AuthFilter;
import com.flowgate.cache.ResponseCache;
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
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Optional;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class GatewayRoutes {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayRoutes.class);

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(
            WebClient webClient, RouteMatcher routeMatcher, RateLimiter rateLimiter,
            RouteCircuitBreakers circuitBreakers, HealthChecker healthChecker,
            AuthFilter authFilter, RoundRobinStrategy roundRobin, LeastConnectionsStrategy leastConn, ResponseCache responseCache, io.micrometer.core.instrument.MeterRegistry meterRegistry,
            com.flowgate.observability.GatewayEventBus eventBus,
            com.flowgate.analyzer.AnomalyDetector anomalyDetector) {

        return RouterFunctions.route(all(), request -> {
            String authHeader = request.headers().firstHeader("Authorization");
            Optional<String> clientId = authFilter.validateAndExtractClientId(authHeader);

            if (clientId.isEmpty()) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .bodyValue("Missing or invalid authentication token.");
            }

            // Right after successful auth + before rate limiting (or after — let's check cache before spending a rate-limit token, since a cache hit costs nothing downstream anyway):

            if (request.method() == HttpMethod.GET) {
                String cacheKey = responseCache.buildKey(request.method().name(), request.path(), clientId.get());
                ResponseCache.CachedResponse cached = responseCache.get(cacheKey);
                if (cached != null) {
                    log.info("Cache HIT for path={} client={}", request.path(), clientId.get());
                    meterRegistry.counter("flowgate.cache.hits").increment();
                    return ServerResponse.status(cached.statusCode())
                            .header("X-Cache", "HIT")   // useful for verifying behavior, see below
                            .bodyValue(cached.body());
                }
            }

            return routeMatcher.match(request.path())
                    .map(route -> rateLimiter.checkLimit(clientId.get(), route.id())
                            .flatMap(result -> {
                                if (!result.allowed()) {
                                    log.warn("Rate limit exceeded for client={} route={}", clientId.get(), route.id());
                                    long retryAfter = rateLimiter.estimateRetryAfterSeconds(result.tokensRemaining());
                                    meterRegistry.counter("flowgate.ratelimit.rejections", "route", route.id()).increment();
                                    eventBus.publish(com.flowgate.observability.GatewayEvent.rateLimitRejected(route.id()));
                                    return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                            .header("Retry-After", String.valueOf(retryAfter))
                                            .bodyValue("Rate limit exceeded. Retry after " + retryAfter + "s.");
                                }
                                String cacheKey = responseCache.buildKey(request.method().name(), request.path(), clientId.get());
                                return forward(webClient, request, route, circuitBreakers, healthChecker, roundRobin, leastConn, responseCache, cacheKey, meterRegistry, eventBus, anomalyDetector);
                            }))
                    .orElseGet(() -> ServerResponse.status(HttpStatus.NOT_FOUND)
                            .bodyValue("No route matched: " + request.path()));
        });
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

    private String stripRoutePrefix(String path, String pathPattern) {
        // Turn "/test-latency/**" into "/test-latency" (the static prefix before the wildcard)
        String prefix = pathPattern.replace("/**", "").replace("/*", "");
        if (!prefix.isEmpty() && path.startsWith(prefix)) {
            String stripped = path.substring(prefix.length());
            return stripped.isEmpty() ? "/" : stripped;
        }
        return path;
    }

    private Mono<ServerResponse> forward(
            WebClient webClient, ServerRequest request, RouteDefinition route,
            RouteCircuitBreakers circuitBreakers, HealthChecker healthChecker,
            RoundRobinStrategy roundRobin, LeastConnectionsStrategy leastConn,
            ResponseCache responseCache, String cacheKey, io.micrometer.core.instrument.MeterRegistry meterRegistry,
            com.flowgate.observability.GatewayEventBus eventBus, com.flowgate.analyzer.AnomalyDetector anomalyDetector) {

        List<String> healthyInstances = healthChecker.getHealthyInstances(route);
        String target = selectTarget(route, healthyInstances, roundRobin, leastConn);
        String targetUrl = target + stripRoutePrefix(request.path(), route.pathPattern());
        long startTime = System.currentTimeMillis();

        boolean isCacheable = request.method() == HttpMethod.GET;

        Mono<ServerResponse> call = webClient
                .method(request.method())
                .uri(targetUrl)
                .headers(headers -> headers.addAll(request.headers().asHttpHeaders()))
                .body(request.bodyToMono(byte[].class), byte[].class)
                .exchangeToMono(clientResponse ->
                        clientResponse.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .flatMap(bodyBytes -> {
                                    int statusCode = clientResponse.statusCode().value();

                                    // Store in cache only for successful GET responses
                                    if (isCacheable && clientResponse.statusCode().is2xxSuccessful()) {
                                        responseCache.put(cacheKey,
                                                new ResponseCache.CachedResponse(statusCode, bodyBytes));
                                        meterRegistry.counter("flowgate.cache.misses").increment();
                                    }

                                    log.info("Request completed: path={} route={} target={} status={}",
                                            request.path(), route.id(), target, clientResponse.statusCode().value());
                                    eventBus.publish(com.flowgate.observability.GatewayEvent.requestCompleted(
                                            route.id(), clientResponse.statusCode().value(), false));

                                    long latency = System.currentTimeMillis() - startTime;
                                    anomalyDetector.checkForAnomaly(route.id(), latency);

                                    return ServerResponse
                                            .status(clientResponse.statusCode())
                                            .headers(headers -> headers.addAll(clientResponse.headers().asHttpHeaders()))
                                            .header("X-Cache", "MISS")
                                            .bodyValue(bodyBytes);
                                })
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