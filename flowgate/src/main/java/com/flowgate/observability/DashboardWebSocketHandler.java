package com.flowgate.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class DashboardWebSocketHandler implements WebSocketHandler {

    private final GatewayEventBus eventBus;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // handles Instant serialization correctly

    public DashboardWebSocketHandler(GatewayEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.send(
                eventBus.stream()
                        .map(this::toJson)
                        .map(session::textMessage)
        );
    }

    private String toJson(GatewayEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }
}