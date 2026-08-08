package com.flowgate.observability;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class GatewayEventBus {
    private final Sinks.Many<GatewayEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(GatewayEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<GatewayEvent> stream() {
        return sink.asFlux();
    }
}