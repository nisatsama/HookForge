package com.hookforge.outbox;

import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    //@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            value = """
            SELECT *
            FROM outbox_events
            WHERE status = 'PENDING'
              AND NOT EXISTS (
                  SELECT 1 FROM webhook_events e
                  JOIN tenants t ON t.id = e.tenant_id
                  WHERE e.id = outbox_events.event_id AND t.is_demo = TRUE
              )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<OutboxEvent> lockPendingBatch(
            @Param("batchSize") int batchSize
    );

    long countByStatus(OutboxStatus status);
}
