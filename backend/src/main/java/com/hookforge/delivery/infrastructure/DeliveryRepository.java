package com.hookforge.delivery.infrastructure;

import com.hookforge.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DeliveryRepository
        extends JpaRepository<Delivery, UUID> {

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
}