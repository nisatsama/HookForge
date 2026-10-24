
CREATE TABLE webhook_deliveries (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    event_id UUID NOT NULL REFERENCES webhook_events(id),
                                    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
                                    tenant_id UUID NOT NULL REFERENCES tenants(id),

                                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    attempt_count INTEGER NOT NULL DEFAULT 0,
                                    next_attempt_at TIMESTAMPTZ,
                                    last_attempt_at TIMESTAMPTZ,
                                    delivered_at TIMESTAMPTZ,

                                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT uq_webhook_deliveries_event_endpoint
                                        UNIQUE (event_id, endpoint_id),

                                    CONSTRAINT chk_webhook_deliveries_status
                                        CHECK (
                                            status IN (
                                                       'PENDING',
                                                       'PROCESSING',
                                                       'RETRYING',
                                                       'SUCCEEDED',
                                                       'FAILED',
                                                       'DEAD'
                                                )
                                            ),

                                    CONSTRAINT chk_webhook_deliveries_attempt_count
                                        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_webhook_deliveries_tenant_created
    ON webhook_deliveries(tenant_id, created_at DESC);

CREATE INDEX idx_webhook_deliveries_pending
    ON webhook_deliveries(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRYING');

CREATE INDEX idx_webhook_deliveries_event_id
    ON webhook_deliveries(event_id);

CREATE TABLE delivery_attempts (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   delivery_id UUID NOT NULL REFERENCES webhook_deliveries(id),
                                   attempt_number INTEGER NOT NULL,

                                   status_code INTEGER,
                                   response_body TEXT,
                                   error_message TEXT,
                                   duration_ms BIGINT,

                                   started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   completed_at TIMESTAMPTZ,

                                   CONSTRAINT uq_delivery_attempts_number
                                       UNIQUE (delivery_id, attempt_number),

                                   CONSTRAINT chk_delivery_attempts_number
                                       CHECK (attempt_number > 0),

                                   CONSTRAINT chk_delivery_attempts_duration
                                       CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX idx_delivery_attempts_delivery_started
    ON delivery_attempts(delivery_id, started_at DESC);
