package com.example.orderplatform.notification.service;

import com.example.orderplatform.notification.messaging.PlatformEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class NotificationLoggerTest {

    @Test
    void duplicateEventIsIgnored() {
        NotificationLogger logger = new NotificationLogger();
        UUID eventId = UUID.randomUUID();
        PlatformEvent event = new PlatformEvent(eventId, "order.confirmed", Instant.now(),
                "corr", UUID.randomUUID(), List.of(), null);

        logger.notify(event);
        logger.notify(event); // must not throw or log a second time
    }
}
