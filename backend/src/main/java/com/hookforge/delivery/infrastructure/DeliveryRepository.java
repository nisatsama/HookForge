
package com.hookforge.delivery.infrastructure;

import com.hookforge.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying
    @Query(value = """
        INSERT INTO webhook_deliveries (
            event_id,
            endpoint_id,
            tenant_id,
            status,
            attempt_count,
            created_at,
            updated_at
        )
        VALUES (
            :eventId,
            :endpointId,
            :tenantId,
            'PENDING',
            0,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (event_id, endpoint_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("endpointId") UUID endpointId,
            @Param("tenantId") UUID tenantId
    );

    @Query(value = """
        SELECT id
        FROM webhook_deliveries
        WHERE status IN ('PENDING', 'RETRYING')
          AND (
              next_attempt_at IS NULL
              OR next_attempt_at <= CURRENT_TIMESTAMP
          )
        ORDER BY COALESCE(next_attempt_at, created_at), created_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> findReadyDeliveryIds(
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query(value = """
        UPDATE webhook_deliveries
        SET status = 'PROCESSING',
            processing_token = :processingToken,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :deliveryId
          AND status IN ('PENDING', 'RETRYING')
          AND (
              next_attempt_at IS NULL
              OR next_attempt_at <= CURRENT_TIMESTAMP
          )
        """, nativeQuery = true)
    int claimDelivery(
            @Param("deliveryId") UUID deliveryId,
            @Param("processingToken") UUID processingToken
    );

    @Query(value = """
        SELECT id
        FROM webhook_deliveries
        WHERE status = 'PROCESSING'
          AND updated_at < CURRENT_TIMESTAMP
              - (:staleSeconds * INTERVAL '1 second')
        ORDER BY updated_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> findStaleProcessingDeliveryIds(
            @Param("staleSeconds") long staleSeconds,
            @Param("batchSize") int batchSize
    );

    // Legacy bulk recovery method. Prefer DeliveryRecoveryService,
    // which also completes the corresponding attempt history.
    @Modifying
    @Query(value = """
        WITH stale_deliveries AS (
            SELECT id
            FROM webhook_deliveries
            WHERE status = 'PROCESSING'
              AND updated_at < CURRENT_TIMESTAMP
                  - (:staleSeconds * INTERVAL '1 second')
            ORDER BY updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE webhook_deliveries AS d
        SET status = 'RETRYING',
            processing_token = NULL,
            next_attempt_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        FROM stale_deliveries AS s
        WHERE d.id = s.id
          AND d.status = 'PROCESSING'
        """, nativeQuery = true)
    int recoverStaleProcessingDeliveries(
            @Param("staleSeconds") long staleSeconds,
            @Param("batchSize") int batchSize
    );

    boolean existsByEventIdAndEndpointId(
            UUID eventId,
            UUID endpointId
    );
}
