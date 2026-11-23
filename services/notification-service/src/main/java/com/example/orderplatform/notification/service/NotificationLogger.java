package com.example.orderplatform.notification.service;

import com.example.orderplatform.notification.messaging.PlatformEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional notification service: logs confirmations/cancellations.
 * Idempotency is an in-memory set because this service has no database and performs
 * no external side effects. A restart loses the set but only re-emits log lines
 * (see ADR-009).
 */
@Slf4j
@Service
public class NotificationLogger {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    public void notify(PlatformEvent event) {
        if (!processed.add(event.eventId())) {
            log.debug("Ignoring duplicate notification event {}", event.eventId());
            return;
        }
        log.info("Notification: order {} -> {} (correlationId={})",
                event.orderId(), event.type(), event.correlationId());
    }
}
