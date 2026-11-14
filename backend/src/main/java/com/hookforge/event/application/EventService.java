
package com.hookforge.event.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.hookforge.event.api.dto.CreateEventRequest;
import com.hookforge.event.api.dto.EventResponse;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.infrastructure.OutboxEventRepository;
import com.hookforge.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class EventService {

    private final WebhookEventRepository eventRepository;
    private final OutboxEventRepository outboxRepository;

    private final tools.jackson.databind.ObjectMapper jackson3Mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;

    private final TransactionTemplate transactionTemplate;

    public EventService(
            WebhookEventRepository eventRepository,
            OutboxEventRepository outboxRepository,
            tools.jackson.databind.ObjectMapper jackson3Mapper,
            com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
        this.jackson3Mapper = jackson3Mapper;
        this.jackson2Mapper = jackson2Mapper;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }
    public EventResponse getEvent(UUID eventId) {

        UUID tenantId = TenantContext.getTenantId();

        WebhookEvent event = eventRepository
                .findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                ));

        return toResponse(event, false);
    }

    public EventResponse getEvent(UUID tenantId, UUID eventId) {
        WebhookEvent event = eventRepository
                .findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found"));
        return toResponse(event, false);
    }

    public EventPageResponse listEvents(UUID tenantId, int page, int size) {
        var events = eventRepository.findAllByTenantId(tenantId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new EventPageResponse(events.getContent().stream()
                .map(event -> toResponse(event, false)).toList(),
                events.getNumber(), events.getSize(), events.getTotalElements(),
                events.getTotalPages(), events.isLast());
    }

    public record EventPageResponse(java.util.List<EventResponse> items,
                                    int page, int size, long totalElements,
                                    int totalPages, boolean last) { }
    public EventResponse createEvent(
            CreateEventRequest request,
            String idempotencyKey
    ) {
        final String normalizedKey =
                normalizeIdempotencyKey(idempotencyKey);

        UUID tenantId = TenantContext.getTenantId();

        JsonNode payload = convertPayload(request);

        final String requestHash = normalizedKey == null
                ? null
                : fingerprint(request.eventType(), payload);

        try {
            EventResponse response = transactionTemplate.execute(status ->
                    createWithinTransaction(
                            tenantId,
                            request.eventType(),
                            normalizedKey,
                            requestHash,
                            payload
                    )
            );

            if (response == null) {
                throw new IllegalStateException(
                        "Event creation transaction returned no result"
                );
            }

            return response;

        } catch (DataIntegrityViolationException exception) {
            // Only keyed requests can be resolved as idempotent retries.
            if (normalizedKey == null) {
                throw exception;
            }

            // The failed transaction has rolled back. Resolve the
            // concurrent winner in a separate transaction.
            WebhookEvent existing = transactionTemplate.execute(status ->
                    eventRepository
                            .findByTenantIdAndIdempotencyKey(
                                    tenantId,
                                    normalizedKey
                            )
                            .orElse(null)
            );

            // Do not disguise unrelated database constraint violations.
            if (existing == null) {
                throw exception;
            }

            return resolveExisting(
                    existing,
                    requestHash,
                    request.eventType(),
                    payload
            );
        }
    }

    private EventResponse createWithinTransaction(
            UUID tenantId,
            String eventType,
            String idempotencyKey,
            String requestHash,
            JsonNode payload
    ) {
        if (idempotencyKey != null) {
            var existing = eventRepository
                    .findByTenantIdAndIdempotencyKey(
                            tenantId,
                            idempotencyKey
                    );

            if (existing.isPresent()) {
                return resolveExisting(
                        existing.get(),
                        requestHash,
                        eventType,
                        payload
                );
            }
        }

        WebhookEvent event = new WebhookEvent(
                tenantId,
                eventType,
                idempotencyKey,
                requestHash,
                payload
        );

        // Flush here so a concurrent uniqueness violation occurs
        // before we attempt to write the outbox record.
        WebhookEvent savedEvent = eventRepository.saveAndFlush(event);

        OutboxEvent outboxEvent = new OutboxEvent(
                savedEvent.getId(),
                savedEvent.getEventType(),
                savedEvent.getPayload()
        );

        // The event and outbox record commit or roll back together.
        outboxRepository.save(outboxEvent);

        return toResponse(savedEvent, true);
    }

    private EventResponse resolveExisting(
            WebhookEvent existing,
            String incomingHash,
            String incomingEventType,
            JsonNode incomingPayload
    ) {
        boolean identical;

        if (existing.getRequestHash() != null) {
            identical = Objects.equals(
                    existing.getRequestHash().trim(),
                    incomingHash
            );
        } else {
            // Legacy events created before request_hash was introduced.
            identical = Objects.equals(
                    existing.getEventType(),
                    incomingEventType
            ) && Objects.equals(
                    existing.getPayload(),
                    incomingPayload
            );
        }

        if (!identical) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key has already been used "
                            + "with a different event type or payload"
            );
        }

        return toResponse(existing, false);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }

        String normalized = key.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 255 characters"
            );
        }

        return normalized;
    }

    private JsonNode convertPayload(CreateEventRequest request) {
        try {
            return jackson2Mapper.readTree(
                    jackson3Mapper.writeValueAsString(request.payload())
            );
        } catch (
                tools.jackson.core.JacksonException
                | JsonProcessingException exception
        ) {
            throw new IllegalStateException(
                    "Failed to convert event payload between Jackson versions",
                    exception
            );
        }
    }

    private String fingerprint(String eventType, JsonNode payload) {
        try {
            String canonicalRequest =
                    "{\"eventType\":"
                            + jackson2Mapper.writeValueAsString(eventType)
                            + ",\"payload\":"
                            + canonicalize(payload)
                            + "}";

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(
                            canonicalRequest.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(digest);

        } catch (
                JsonProcessingException
                | NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                    "Failed to calculate event request fingerprint",
                    exception
            );
        }
    }

    private String canonicalize(JsonNode node)
            throws JsonProcessingException {

        if (node == null || node.isNull()) {
            return "null";
        }

        if (node.isObject()) {
            Map<String, JsonNode> sortedFields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sortedFields.put(field.getKey(), field.getValue());
            }

            StringBuilder result = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<String, JsonNode> field
                    : sortedFields.entrySet()) {
                if (!first) {
                    result.append(',');
                }

                result.append(
                        jackson2Mapper.writeValueAsString(field.getKey())
                );
                result.append(':');
                result.append(canonicalize(field.getValue()));

                first = false;
            }

            return result.append('}').toString();
        }

        if (node.isArray()) {
            StringBuilder result = new StringBuilder("[");
            Iterator<JsonNode> elements = node.elements();
            boolean first = true;

            while (elements.hasNext()) {
                if (!first) {
                    result.append(',');
                }

                result.append(canonicalize(elements.next()));
                first = false;
            }

            return result.append(']').toString();
        }

        if (node.isTextual()) {
            return jackson2Mapper.writeValueAsString(node.textValue());
        }

        if (node.isNumber()) {
            return node.numberValue().toString();
        }

        return node.toString();
    }

    private EventResponse toResponse(
            WebhookEvent event,
            boolean created
    ) {
        try {
            tools.jackson.databind.JsonNode responsePayload =
                    jackson3Mapper.readTree(
                            jackson2Mapper.writeValueAsString(
                                    event.getPayload()
                            )
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
                | JsonProcessingException exception
        ) {
            throw new IllegalStateException(
                    "Failed to convert persisted event payload for the API response",
                    exception
            );
        }
    }
}
