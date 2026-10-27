
package com.hookforge.event.application;

import com.hookforge.event.api.dto.CreateEventRequest;
import com.hookforge.event.api.dto.EventResponse;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.infrastructure.OutboxEventRepository;
import com.hookforge.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final WebhookEventRepository eventRepository;
    private final OutboxEventRepository outboxRepository;

    private final tools.jackson.databind.ObjectMapper jackson3Mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;

    public EventService(
            WebhookEventRepository eventRepository,
            OutboxEventRepository outboxRepository,
            tools.jackson.databind.ObjectMapper jackson3Mapper,
            com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper
    ) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.jackson3Mapper = jackson3Mapper;
        this.jackson2Mapper = jackson2Mapper;
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

        var tenantId = TenantContext.getTenantId();

        if (idempotencyKey != null) {
            var existing = eventRepository
                    .findByTenantIdAndIdempotencyKey(
                            tenantId,
                            idempotencyKey
                    );

            if (existing.isPresent()) {
                WebhookEvent event = existing.get();

                return toResponse(event, false);
            }
        }

        try {
            // Convert the HTTP payload (Jackson 3) to the
            // Jackson 2 tree type used by Hibernate persistence.
            com.fasterxml.jackson.databind.JsonNode persistedPayload =
                    jackson2Mapper.readTree(
                            jackson3Mapper.writeValueAsString(request.payload())
                    );

            WebhookEvent event = new WebhookEvent(
                    tenantId,
                    request.eventType(),
                    idempotencyKey,
                    persistedPayload
            );

            WebhookEvent savedEvent = eventRepository.save(event);

            // Save the outbox row in the same transaction.
            OutboxEvent outboxEvent = new OutboxEvent(
                    savedEvent.getId(),
                    savedEvent.getEventType(),
                    savedEvent.getPayload()
            );

            outboxRepository.save(outboxEvent);

            return toResponse(savedEvent, true);

        } catch (
                tools.jackson.core.JacksonException
                | com.fasterxml.jackson.core.JsonProcessingException ex
        ) {
            throw new IllegalStateException(
                    "Failed to convert event payload between Jackson versions",
                    ex
            );
        }
    }

    private EventResponse toResponse(
            WebhookEvent event,
            boolean created
    ) {
        try {
            // Convert the persisted Jackson 2 tree back to Jackson 3
            // for the Spring Boot 4 API response.
            tools.jackson.databind.JsonNode responsePayload =
                    jackson3Mapper.readTree(
                            jackson2Mapper.writeValueAsString(event.getPayload())
                    );

            return new EventResponse(
                    event.getId(),
                    event.getEventType(),
                    responsePayload,
                    event.getCreatedAt(),
                    created
            );

        } catch (
                tools.jackson.core.JacksonException
                | com.fasterxml.jackson.core.JsonProcessingException ex
        ) {
            throw new IllegalStateException(
                    "Failed to convert persisted event payload for the API response",
                    ex
            );
        }
    }
}
