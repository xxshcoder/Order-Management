package com.example.orderplatform.order.service;

import com.example.orderplatform.order.domain.OutboxEvent;
import com.example.orderplatform.order.domain.OutboxStatus;
import com.example.orderplatform.order.messaging.PlatformEvent;
import com.example.orderplatform.order.repository.OutboxRepository;
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
            outboxRepository.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxStatus.NEW)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event for topic " + topic, e);
        }
    }
}
