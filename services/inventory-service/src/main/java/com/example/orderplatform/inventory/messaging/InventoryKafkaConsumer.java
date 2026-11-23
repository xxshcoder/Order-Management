package com.example.orderplatform.inventory.messaging;

import com.example.orderplatform.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryKafkaConsumer {

    private final StockReservationService stockReservationService;

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "inventory-order-created")
    public void onOrderCreated(PlatformEvent event) {
        withCorrelation(event, () -> stockReservationService.reserve(event));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = "inventory-order-cancelled")
    public void onOrderCancelled(PlatformEvent event) {
        withCorrelation(event, () -> stockReservationService.release(event));
    }

    private void withCorrelation(PlatformEvent event, Runnable action) {
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", event.correlationId());
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
