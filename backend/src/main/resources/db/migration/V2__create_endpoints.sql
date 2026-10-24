
CREATE TABLE webhook_endpoints (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   tenant_id UUID NOT NULL REFERENCES tenants(id),
                                   name VARCHAR(150) NOT NULL,
                                   url TEXT NOT NULL,
                                   secret_hash VARCHAR(255) NOT NULL,
                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT chk_webhook_endpoints_status
                                       CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_webhook_endpoints_tenant_id
    ON webhook_endpoints(tenant_id);

CREATE INDEX idx_webhook_endpoints_tenant_status
    ON webhook_endpoints(tenant_id, status);
