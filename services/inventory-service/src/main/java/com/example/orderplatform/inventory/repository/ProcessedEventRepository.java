package com.example.orderplatform.inventory.repository;

import com.example.orderplatform.inventory.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById(UUID) is inherited and is the idempotency primitive for all consumers.
}
