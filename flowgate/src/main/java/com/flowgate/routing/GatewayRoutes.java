package com.flowgate.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class GatewayRoutes {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("http://dummy-backend:80") // dummy-backend, mapped to host port 9000
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> proxyRoute(WebClient webClient) {
        return RouterFunctions.route(
                all(), // matches every incoming request, regardless of path/method
                request -> webClient
                        .method(request.method())
                        .uri(request.uri().getPath())
                        .headers(headers -> headers.addAll(request.headers().asHttpHeaders()))
                        .body(request.bodyToMono(byte[].class), byte[].class)
                        .exchangeToMono(clientResponse ->
                                ServerResponse
                                        .status(clientResponse.statusCode())
                                        .headers(headers -> headers.addAll(clientResponse.headers().asHttpHeaders()))
                                        .body(clientResponse.bodyToMono(byte[].class), byte[].class)
                        )
        );
    }
}