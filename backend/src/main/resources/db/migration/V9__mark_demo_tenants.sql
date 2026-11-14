ALTER TABLE tenants
    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_tenants_demo
    ON tenants (id)
    WHERE is_demo = TRUE;
