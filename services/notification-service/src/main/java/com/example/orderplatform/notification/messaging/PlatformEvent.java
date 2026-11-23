package com.example.orderplatform.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
}
