package com.example.orderplatform.inventory.repository;

import com.example.orderplatform.inventory.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    /**
     * Claim a batch of unpublished rows. SKIP LOCKED lets multiple instances publish
     * concurrently without sending the same event twice.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'NEW'
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findBatch(@Param("limit") int limit);
}
