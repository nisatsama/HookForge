package com.hookforge.event.application;

import com.hookforge.event.api.dto.CreateEventRequest;
import com.hookforge.event.api.dto.EventResponse;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.infrastructure.OutboxEventRepository;
import com.hookforge.tenant.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final WebhookEventRepository eventRepository;
    private final OutboxEventRepository outboxRepository;
    private final TenantContext tenantContext;

    public EventService(
            WebhookEventRepository eventRepository,
            OutboxEventRepository outboxRepository,
            TenantContext tenantContext
    ) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public EventResponse createEvent(
            CreateEventRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.trim();

            if (idempotencyKey.isBlank()) {
                idempotencyKey = null;
            } else if (idempotencyKey.length() > 255) {
                throw new IllegalArgumentException(
                        "Idempotency-Key must not exceed 255 characters"
                );
            }
        }

        var tenantId = tenantContext.getTenantId();

        // Return the existing event for a repeated idempotency key.
        if (idempotencyKey != null) {
            var existing = eventRepository
                    .findByTenantIdAndIdempotencyKey(
                            tenantId,
                            idempotencyKey
                    );

            if (existing.isPresent()) {
                WebhookEvent event = existing.get();

                return new EventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getPayload(),
                        event.getCreatedAt(),
                        false
                );
            }
        }

        WebhookEvent event = new WebhookEvent(
                tenantId,
                request.eventType(),
                idempotencyKey,
                request.payload()
        );

        WebhookEvent savedEvent = eventRepository.save(event);

        // The event and outbox record must commit or roll back together.
        OutboxEvent outboxEvent = new OutboxEvent(
                savedEvent.getId(),
                savedEvent.getEventType(),
                savedEvent.getPayload()
        );

        outboxRepository.save(outboxEvent);

        return new EventResponse(
                savedEvent.getId(),
                savedEvent.getEventType(),
                savedEvent.getPayload(),
                savedEvent.getCreatedAt(),
                true
        );
    }
}