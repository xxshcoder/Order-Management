package com.example.orderplatform.notification.messaging;

import com.example.orderplatform.notification.service.NotificationLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationLogger notificationLogger;

    @KafkaListener(topics = {Topics.ORDER_CONFIRMED, Topics.ORDER_CANCELLED},
            groupId = "notification-service")
    public void onOrderEvent(PlatformEvent event) {
        MDC.put("correlationId", event.correlationId());
        try {
            notificationLogger.notify(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
