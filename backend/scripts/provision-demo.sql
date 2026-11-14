\if :{?demo_tenant_id}
\else
\echo 'Required: psql -v demo_tenant_id=<UUID>'
\quit 3
\endif

BEGIN;
SELECT set_config('hookforge.demo_tenant_id', :'demo_tenant_id', TRUE) \gset

DO $$
DECLARE
    demo_id UUID := current_setting('hookforge.demo_tenant_id')::UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM tenants WHERE slug = 'acme-commerce-demo' AND id <> demo_id) THEN
        RAISE EXCEPTION 'Demo slug already belongs to another tenant; refusing to modify it';
    END IF;
    IF EXISTS (SELECT 1 FROM tenants WHERE id = demo_id AND (slug <> 'acme-commerce-demo' OR is_demo = FALSE)) THEN
        RAISE EXCEPTION 'Configured demo tenant ID belongs to a non-demo or differently configured tenant';
    END IF;
END $$;

INSERT INTO tenants (id, name, slug, status, is_demo)
VALUES (current_setting('hookforge.demo_tenant_id')::UUID, 'Acme Commerce — Demo Workspace', 'acme-commerce-demo', 'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

WITH endpoints AS (
    SELECT i,
           md5(current_setting('hookforge.demo_tenant_id') || ':endpoint:' || i)::UUID AS id,
           (ARRAY['Orders Service','Inventory Service','Customer Notifications','Analytics Pipeline','Fraud Monitor'])[i] AS name,
           ('https://receiver-' || i || '.example.invalid/hooks') AS url
    FROM generate_series(1, 5) AS i
)
INSERT INTO webhook_endpoints (id, tenant_id, name, url, secret_hash, status, secret_encrypted)
SELECT id, current_setting('hookforge.demo_tenant_id')::UUID, name, url, 'demo-fixture-no-usable-secret', 'ACTIVE', NULL
FROM endpoints
ON CONFLICT (id) DO NOTHING;

WITH events AS (
    SELECT i,
           md5(current_setting('hookforge.demo_tenant_id') || ':event:' || i)::UUID AS id,
           (ARRAY['order.created','order.paid','order.shipped','inventory.low_stock','customer.updated','payment.failed'])[((i - 1) % 6) + 1] AS event_type,
           jsonb_build_object(
               'demo', TRUE,
               'sequence', i,
               'source', 'acme-commerce',
               'data', CASE (i - 1) % 6
                   WHEN 0 THEN jsonb_build_object('orderId', 'ord_demo_' || lpad(i::TEXT, 5, '0'), 'currency', 'USD', 'total', (49.99 + i)::NUMERIC(10,2))
                   WHEN 1 THEN jsonb_build_object('orderId', 'ord_demo_' || lpad(i::TEXT, 5, '0'), 'amount', (49.99 + i)::NUMERIC(10,2), 'currency', 'USD')
                   WHEN 2 THEN jsonb_build_object('orderId', 'ord_demo_' || lpad(i::TEXT, 5, '0'), 'carrier', 'ParcelPost', 'tracking', 'DEMO' || lpad(i::TEXT, 8, '0'))
                   WHEN 3 THEN jsonb_build_object('sku', 'SKU-' || lpad(i::TEXT, 4, '0'), 'available', i % 8, 'threshold', 10)
                   WHEN 4 THEN jsonb_build_object('customerId', 'cus_demo_' || lpad(i::TEXT, 5, '0'), 'tier', 'standard')
                   ELSE jsonb_build_object('paymentId', 'pay_demo_' || lpad(i::TEXT, 5, '0'), 'reason', 'issuer_declined', 'retryable', FALSE)
               END
           ) AS payload
    FROM generate_series(1, 40) AS i
)
INSERT INTO webhook_events (id, tenant_id, event_type, idempotency_key, payload, created_at)
SELECT id, current_setting('hookforge.demo_tenant_id')::UUID, event_type,
       'demo-seed-v1:event:' || i, payload, CURRENT_TIMESTAMP - (i || ' hours')::INTERVAL
FROM events
ON CONFLICT (id) DO NOTHING;

WITH fixture AS (
    SELECT i,
           md5(current_setting('hookforge.demo_tenant_id') || ':delivery:' || i)::UUID AS id,
           md5(current_setting('hookforge.demo_tenant_id') || ':event:' || (((i - 1) / 5) + 1))::UUID AS event_id,
           md5(current_setting('hookforge.demo_tenant_id') || ':endpoint:' || (((i - 1) % 5) + 1))::UUID AS endpoint_id,
           CASE WHEN i <= 46 THEN 'SUCCEEDED'
                WHEN i <= 55 THEN 'FAILED'
                WHEN i <= 62 THEN 'DEAD'
                WHEN i <= 69 THEN 'RETRYING'
                ELSE 'PENDING' END AS status,
           CASE WHEN i <= 46 THEN CASE WHEN i % 9 = 0 THEN 2 ELSE 1 END
                WHEN i <= 55 THEN 2
                WHEN i <= 62 THEN 5
                WHEN i <= 69 THEN 2 ELSE 0 END AS attempt_count,
           CURRENT_TIMESTAMP - (i || ' hours')::INTERVAL AS created_at
    FROM generate_series(1, 76) AS i
)
INSERT INTO webhook_deliveries (id, event_id, endpoint_id, tenant_id, status, attempt_count,
                                next_attempt_at, last_attempt_at, delivered_at, created_at, updated_at)
SELECT id, event_id, endpoint_id, current_setting('hookforge.demo_tenant_id')::UUID,
       status, attempt_count,
       CASE WHEN status = 'RETRYING' THEN CURRENT_TIMESTAMP + INTERVAL '1 day' END,
       CASE WHEN attempt_count > 0 THEN created_at + INTERVAL '2 minutes' END,
       CASE WHEN status = 'SUCCEEDED' THEN created_at + INTERVAL '3 minutes' END,
       created_at, created_at + INTERVAL '3 minutes'
FROM fixture
ON CONFLICT (id) DO NOTHING;

WITH fixture AS (
    SELECT i,
           md5(current_setting('hookforge.demo_tenant_id') || ':delivery:' || i)::UUID AS delivery_id,
           CASE WHEN i <= 46 THEN CASE WHEN i % 9 = 0 THEN 2 ELSE 1 END
                WHEN i <= 55 THEN 2
                WHEN i <= 62 THEN 5
                WHEN i <= 69 THEN 2 ELSE 0 END AS attempt_count,
           CASE WHEN i <= 46 THEN 'SUCCEEDED'
                WHEN i <= 55 THEN 'FAILED'
                WHEN i <= 62 THEN 'DEAD'
                WHEN i <= 69 THEN 'RETRYING'
                ELSE 'PENDING' END AS status,
           CURRENT_TIMESTAMP - (i || ' hours')::INTERVAL AS created_at
    FROM generate_series(1, 76) AS i
)
INSERT INTO delivery_attempts (id, delivery_id, attempt_number, status_code, response_body,
                               error_message, duration_ms, started_at, completed_at)
SELECT md5(current_setting('hookforge.demo_tenant_id') || ':attempt:' || i || ':' || n)::UUID,
       delivery_id, n,
       CASE WHEN status = 'SUCCEEDED' AND n = attempt_count THEN 200
            WHEN status = 'RETRYING' THEN 429
            WHEN status = 'DEAD' THEN 503 ELSE 500 END,
       CASE WHEN status = 'SUCCEEDED' AND n = attempt_count THEN '{"accepted":true,"demo":true}'
            ELSE '{"message":"sample receiver response","demo":true}' END,
       CASE WHEN status = 'SUCCEEDED' AND n = attempt_count THEN NULL
            WHEN status = 'RETRYING' THEN 'Sample receiver requested a retry'
            WHEN status = 'DEAD' THEN 'Sample receiver remained unavailable'
            ELSE 'Sample receiver returned a transient error' END,
       (45 + i * 11 + n * 17)::BIGINT,
       created_at + (n || ' minutes')::INTERVAL,
       created_at + (n || ' minutes')::INTERVAL + INTERVAL '1 second'
FROM fixture CROSS JOIN LATERAL generate_series(1, attempt_count) AS n
ON CONFLICT (id) DO NOTHING;

DO $$
DECLARE
    demo_id UUID := current_setting('hookforge.demo_tenant_id')::UUID;
    i INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = demo_id AND name = 'Acme Commerce — Demo Workspace' AND slug = 'acme-commerce-demo' AND status = 'ACTIVE' AND is_demo = TRUE) THEN
        RAISE EXCEPTION 'Configured demo tenant does not match expected fixture identity';
    END IF;
    IF (SELECT COUNT(*) FROM webhook_endpoints WHERE tenant_id = demo_id) <> 5
       OR EXISTS (SELECT 1 FROM webhook_endpoints WHERE tenant_id = demo_id AND (url NOT LIKE 'https://receiver-%.example.invalid/hooks' OR secret_encrypted IS NOT NULL OR status <> 'ACTIVE' OR secret_hash <> 'demo-fixture-no-usable-secret')) THEN
        RAISE EXCEPTION 'Endpoint fixtures conflict with the expected safe demo dataset; transaction rolled back';
    END IF;
    FOR i IN 1..5 LOOP
        IF NOT EXISTS (
            SELECT 1 FROM webhook_endpoints ep
            WHERE ep.id = md5(demo_id::TEXT || ':endpoint:' || i)::UUID
              AND ep.tenant_id = demo_id
              AND ep.name = (ARRAY['Orders Service','Inventory Service','Customer Notifications','Analytics Pipeline','Fraud Monitor'])[i]
              AND ep.url = 'https://receiver-' || i || '.example.invalid/hooks'
              AND ep.status = 'ACTIVE'
              AND ep.secret_hash = 'demo-fixture-no-usable-secret'
              AND ep.secret_encrypted IS NULL
        ) THEN
            RAISE EXCEPTION 'An endpoint fixture ID conflicts with existing data';
        END IF;
    END LOOP;
    IF (SELECT COUNT(*) FROM webhook_events WHERE tenant_id = demo_id) <> 40
       OR (SELECT COUNT(*) FROM webhook_deliveries WHERE tenant_id = demo_id) <> 76 THEN
        RAISE EXCEPTION 'Demo fixture IDs conflict with existing data; transaction rolled back';
    END IF;
    IF (SELECT COUNT(*) FROM webhook_events e WHERE e.tenant_id = demo_id AND e.id IN (
            SELECT md5(demo_id::TEXT || ':event:' || i)::UUID FROM generate_series(1,40) AS i
        )) <> 40
       OR (SELECT COUNT(*) FROM webhook_deliveries d WHERE d.tenant_id = demo_id AND d.id IN (
            SELECT md5(demo_id::TEXT || ':delivery:' || i)::UUID FROM generate_series(1,76) AS i
        )) <> 76 THEN
        RAISE EXCEPTION 'Deterministic fixture rows conflict with existing tenant data';
    END IF;
    IF (SELECT COUNT(*) FROM delivery_attempts a JOIN webhook_deliveries d ON d.id = a.delivery_id WHERE d.tenant_id = demo_id) <> 118 THEN
        RAISE EXCEPTION 'Demo attempt fixtures are incomplete or conflict with existing data';
    END IF;
    IF EXISTS (
        SELECT 1 FROM webhook_deliveries d
        JOIN webhook_events e ON e.id = d.event_id
        JOIN webhook_endpoints ep ON ep.id = d.endpoint_id
        WHERE d.tenant_id = demo_id AND (e.tenant_id <> demo_id OR ep.tenant_id <> demo_id)
    ) THEN
        RAISE EXCEPTION 'Demo fixture relationship crossed tenant boundary';
    END IF;
END $$;

COMMIT;
