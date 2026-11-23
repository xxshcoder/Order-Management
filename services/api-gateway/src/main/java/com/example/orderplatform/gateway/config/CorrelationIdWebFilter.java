package com.example.orderplatform.gateway.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/** Generates or propagates X-Correlation-Id and puts it in the Reactor context and MDC. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    @Value("${app.correlation.header:X-Correlation-Id}")
    private String headerName;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(headerName);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String id = correlationId;
        exchange.getResponse().getHeaders().set(headerName, id);
        exchange.getAttributes().put("correlationId", id);
        MDC.put("correlationId", id);
        return chain.filter(exchange)
                .doOnEach(signal -> MDC.put("correlationId", id))
                .doFinally(signal -> MDC.remove("correlationId"))
                .contextWrite(Context.of("correlationId", id));
    }
}
