package com.flowgate.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class GatewayRoutes {

    @Bean
    public WebClient webClient() {
        // No fixed baseUrl anymore — target is resolved per-request from RouteMatcher.
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(WebClient webClient, RouteMatcher routeMatcher) {
        return RouterFunctions.route(all(), request ->
                routeMatcher.match(request.path())
                        .map(route -> forward(webClient, request, route))
                        .orElseGet(() -> ServerResponse.status(HttpStatus.NOT_FOUND)
                                .bodyValue("No route matched: " + request.path()))
        );
    }

    private reactor.core.publisher.Mono<ServerResponse> forward(
            WebClient webClient,
            org.springframework.web.reactive.function.server.ServerRequest request,
            RouteDefinition route) {

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