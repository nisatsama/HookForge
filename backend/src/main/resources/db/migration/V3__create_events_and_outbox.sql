
CREATE TABLE webhook_events (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                tenant_id UUID NOT NULL REFERENCES tenants(id),
                                event_type VARCHAR(150) NOT NULL,
                                idempotency_key VARCHAR(255),
                                payload JSONB NOT NULL,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT uq_webhook_events_tenant_idempotency
                                    UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_webhook_events_tenant_created
    ON webhook_events(tenant_id, created_at DESC);

CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               event_id UUID NOT NULL REFERENCES webhook_events(id),
                               event_type VARCHAR(150) NOT NULL,
                               payload JSONB NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               published_at TIMESTAMPTZ,
                               attempts INTEGER NOT NULL DEFAULT 0,

                               CONSTRAINT chk_outbox_events_status
                                   CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),

                               CONSTRAINT chk_outbox_events_attempts
                                   CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events(created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_event_id
    ON outbox_events(event_id);
