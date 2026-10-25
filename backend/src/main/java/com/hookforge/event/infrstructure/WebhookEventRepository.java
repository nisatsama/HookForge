package com.hookforge.event.infrastructure;

import com.hookforge.event.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByTenantIdAndIdempotencyKey(
            UUID tenantId,
            String idempotencyKey
    );

    Optional<WebhookEvent> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );
}