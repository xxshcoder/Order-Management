package com.example.orderplatform.inventory.service;

import com.example.orderplatform.inventory.domain.OutboxEvent;
import com.example.orderplatform.inventory.domain.OutboxStatus;
import com.example.orderplatform.inventory.messaging.PlatformEvent;
import com.example.orderplatform.inventory.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Appends events to the outbox INSIDE the caller's database transaction. */
@Component
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void append(UUID aggregateId, String topic, PlatformEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(payload)
                    .status(OutboxStatus.NEW)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event for topic " + topic, e);
        }
    }
}
