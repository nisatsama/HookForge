
package com.hookforge.event.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_webhook_events_tenant_idempotency",
                columnNames = {"tenant_id", "idempotency_key"}
        )
)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(
            UUID tenantId,
            String eventType,
            String idempotencyKey,
            String requestHash,
            JsonNode payload
    ) {
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.payload = payload;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
