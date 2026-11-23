package com.example.orderplatform.order.messaging;

import com.example.orderplatform.order.domain.OutboxEvent;
import com.example.orderplatform.order.domain.OutboxStatus;
import com.example.orderplatform.order.metrics.OrderMetrics;
import com.example.orderplatform.order.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Sends NEW outbox rows with SKIP LOCKED and marks them SENT after the broker ack. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, PlatformEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderMetrics metrics;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outboxRepository.findBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        metrics.setOutboxBacklog(batch.size());
        for (OutboxEvent row : batch) {
            try {
                PlatformEvent event = objectMapper.readValue(row.getPayload(), PlatformEvent.class);
                kafkaTemplate.send(row.getTopic(), event.orderId().toString(), event)
                        .get(5, TimeUnit.SECONDS);
                row.setStatus(OutboxStatus.SENT);
                row.setSentAt(Instant.now());
                outboxRepository.save(row);
            } catch (Exception e) {
                log.error("Failed to publish outbox row {} to {}", row.getId(), row.getTopic(), e);
                break;
            }
        }
    }
}
