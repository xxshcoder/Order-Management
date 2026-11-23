package com.example.orderplatform.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderMetrics {

    private final Counter created;
    private final Counter confirmed;
    private final Counter cancelled;
    private final AtomicLong outboxBacklog = new AtomicLong();

    public OrderMetrics(MeterRegistry registry) {
        this.created = Counter.builder("orders.created.total")
                .description("Orders created").register(registry);
        this.confirmed = Counter.builder("orders.confirmed.total")
                .description("Orders confirmed after stock reservation").register(registry);
        this.cancelled = Counter.builder("orders.cancelled.total")
                .description("Orders cancelled (rejection or explicit cancel)").register(registry);
        Gauge.builder("outbox.backlog", outboxBacklog, AtomicLong::get)
                .description("Number of unpublished order outbox rows").register(registry);
    }

    public void orderCreated() { created.increment(); }
    public void orderConfirmed() { confirmed.increment(); }
    public void orderCancelled() { cancelled.increment(); }
    public void setOutboxBacklog(long value) { outboxBacklog.set(value); }
}
