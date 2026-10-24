
CREATE TABLE tenants (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name VARCHAR(150) NOT NULL,
                         slug VARCHAR(100) NOT NULL UNIQUE,
                         status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT chk_tenants_status
                             CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE api_keys (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          tenant_id UUID NOT NULL REFERENCES tenants(id),
                          name VARCHAR(100) NOT NULL,
                          key_prefix VARCHAR(20) NOT NULL,
                          key_hash VARCHAR(255) NOT NULL UNIQUE,
                          last_used_at TIMESTAMPTZ,
                          expires_at TIMESTAMPTZ,
                          revoked_at TIMESTAMPTZ,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_keys_tenant_id
    ON api_keys(tenant_id);
