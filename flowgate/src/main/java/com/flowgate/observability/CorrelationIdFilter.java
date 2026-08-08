package com.flowgate.observability;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // run before every other filter/handler
public class CorrelationIdFilter implements WebFilter {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = UUID.randomUUID().toString();

        // Echo it back to the client too — useful for a client-side debugging story:
        // "give me your X-Correlation-ID and I can find your exact request in our logs."
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange)
                .contextWrite(Context.of(CORRELATION_ID_KEY, correlationId))
                .doOnEach(signal -> {
                    if (signal.getContextView().hasKey(CORRELATION_ID_KEY)) {
                        MDC.put(CORRELATION_ID_KEY, signal.getContextView().get(CORRELATION_ID_KEY));
                    }
                })
                .doFinally(signalType -> MDC.remove(CORRELATION_ID_KEY));
    }
}