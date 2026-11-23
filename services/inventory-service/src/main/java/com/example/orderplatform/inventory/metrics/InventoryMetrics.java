package com.example.orderplatform.inventory.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class InventoryMetrics {

    private final Counter reservationFailures;
    private final AtomicLong outboxBacklog = new AtomicLong();

    public InventoryMetrics(MeterRegistry registry) {
        this.reservationFailures = Counter.builder("reservations.failed.total")
                .description("Number of rejected stock reservations")
                .register(registry);
        Gauge.builder("outbox.backlog", outboxBacklog, AtomicLong::get)
                .description("Number of unpublished inventory outbox rows")
                .register(registry);
    }

    public void reservationRejected() {
        reservationFailures.increment();
    }

    public void setOutboxBacklog(long value) {
        outboxBacklog.set(value);
    }
}
