package com.example.orderplatform.order.repository;

import com.example.orderplatform.order.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'NEW'
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findBatch(@Param("limit") int limit);
}
