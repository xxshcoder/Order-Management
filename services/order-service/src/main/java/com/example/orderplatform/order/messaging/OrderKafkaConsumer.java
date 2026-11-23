package com.example.orderplatform.order.messaging;

import com.example.orderplatform.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = {Topics.STOCK_RESERVED, Topics.STOCK_REJECTED},
            groupId = "order-stock-results")
    public void onStockResult(PlatformEvent event) {
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", event.correlationId());
        try {
            orderService.handleStockResult(event);
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
