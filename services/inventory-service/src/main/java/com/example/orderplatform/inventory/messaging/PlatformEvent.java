package com.example.orderplatform.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single Kafka envelope for every topic. The {@code type} field discriminates the event.
 * The same shape is used (as an independent class) in every service so services stay decoupled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlatformEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        List<Item> items,
        String reason
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productId, int quantity) {
    }

    public static PlatformEvent of(String type, String correlationId, UUID orderId, List<Item> items, String reason) {
        return new PlatformEvent(UUID.randomUUID(), type, Instant.now(), correlationId, orderId, items, reason);
    }
}
