package com.flowgate.routing;

import com.flowgate.ratelimit.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class GatewayRoutes {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(
            WebClient webClient, RouteMatcher routeMatcher, RateLimiter rateLimiter) {

        return RouterFunctions.route(all(), request ->
                rateLimiter.checkLimit(clientId(request))
                        .flatMap(result -> {
                            if (!result.allowed()) {
                                long retryAfter = rateLimiter.estimateRetryAfterSeconds(result.tokensRemaining());
                                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .header("Retry-After", String.valueOf(retryAfter))
                                        .bodyValue("Rate limit exceeded. Retry after " + retryAfter + "s.");
                            }
                            return routeMatcher.match(request.path())
                                    .map(route -> forward(webClient, request, route))
                                    .orElseGet(() -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                            .bodyValue("No route matched: " + request.path()));
                        })
        );
    }

    private String clientId(ServerRequest request) {
        // Temporary identity signal until Phase 5 adds real authentication.
        return request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
    }

    private Mono<ServerResponse> forward(WebClient webClient, ServerRequest request, RouteDefinition route) {
        String targetUrl = route.targetUri() + request.path();
        return webClient
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
    }
}