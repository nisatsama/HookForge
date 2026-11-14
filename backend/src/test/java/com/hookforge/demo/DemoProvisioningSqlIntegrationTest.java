package com.hookforge.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DemoProvisioningSqlIntegrationTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("demo_seed_test").withUsername("test").withPassword("test")
            .withEnv("PGPASSWORD","test");
    static final UUID DEMO_ID=UUID.fromString("84ea58b7-98bd-4114-9d5a-0114c32674c2");
    static final String SCRIPT="/tmp/provision-demo.sql";

    @BeforeAll static void start() throws Exception {
        POSTGRES.start();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/provision-demo.sql").toAbsolutePath()),SCRIPT);
        try(var connection=POSTGRES.createConnection(""); var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE tenants(id UUID PRIMARY KEY,name TEXT NOT NULL,slug TEXT UNIQUE NOT NULL,status TEXT NOT NULL,is_demo BOOLEAN NOT NULL)");
            statement.execute("CREATE TABLE webhook_endpoints(id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(id),name TEXT NOT NULL,url TEXT NOT NULL,secret_hash TEXT NOT NULL,status TEXT NOT NULL,secret_encrypted TEXT)");
            statement.execute("CREATE TABLE webhook_events(id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(id),event_type TEXT NOT NULL,idempotency_key TEXT UNIQUE,payload JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL,UNIQUE(tenant_id,idempotency_key))");
            statement.execute("CREATE TABLE webhook_deliveries(id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES webhook_events(id),endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),tenant_id UUID NOT NULL REFERENCES tenants(id),status TEXT NOT NULL,attempt_count INT NOT NULL,next_attempt_at TIMESTAMPTZ,last_attempt_at TIMESTAMPTZ,delivered_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,UNIQUE(event_id,endpoint_id))");
            statement.execute("CREATE TABLE delivery_attempts(id UUID PRIMARY KEY,delivery_id UUID NOT NULL REFERENCES webhook_deliveries(id),attempt_number INT NOT NULL,status_code INT,response_body TEXT,error_message TEXT,duration_ms BIGINT,started_at TIMESTAMPTZ NOT NULL,completed_at TIMESTAMPTZ,UNIQUE(delivery_id,attempt_number))");
            statement.execute("CREATE TABLE api_keys(id UUID PRIMARY KEY,tenant_id UUID NOT NULL REFERENCES tenants(id))");
            statement.execute("CREATE TABLE outbox_events(id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES webhook_events(id))");
        }
    }

    @Test void provisioningIsRepeatableAndRefusesToOverwriteFixtures() throws Exception {
        assertEquals(0,runProvisioning());
        assertEquals(0,runProvisioning());
        assertEquals("5",query("SELECT count(*) FROM webhook_endpoints WHERE tenant_id='"+DEMO_ID+"'"));
        assertEquals("40",query("SELECT count(*) FROM webhook_events WHERE tenant_id='"+DEMO_ID+"'"));
        assertEquals("76",query("SELECT count(*) FROM webhook_deliveries WHERE tenant_id='"+DEMO_ID+"'"));
        assertEquals("118",query("SELECT count(*) FROM delivery_attempts a JOIN webhook_deliveries d ON d.id=a.delivery_id WHERE d.tenant_id='"+DEMO_ID+"'"));
        assertEquals("0",query("SELECT count(*) FROM api_keys WHERE tenant_id='"+DEMO_ID+"'"));
        assertEquals("0",query("SELECT count(*) FROM outbox_events o JOIN webhook_events e ON e.id=o.event_id WHERE e.tenant_id='"+DEMO_ID+"'"));
        assertEquals("0",query("SELECT count(*) FROM webhook_deliveries d JOIN webhook_events e ON e.id=d.event_id WHERE d.tenant_id='"+DEMO_ID+"' AND e.tenant_id<>'"+DEMO_ID+"'"));

        POSTGRES.execInContainer("psql","-U","test","-d","demo_seed_test","-c",
                "UPDATE webhook_endpoints SET url='https://changed.example.com/hook' WHERE tenant_id='"+DEMO_ID+"' AND name='Orders Service'");
        assertNotEquals(0,runProvisioning());
        assertEquals("https://changed.example.com/hook",query("SELECT url FROM webhook_endpoints WHERE tenant_id='"+DEMO_ID+"' AND name='Orders Service'"));
    }

    private static int runProvisioning() throws Exception {
        return POSTGRES.execInContainer("psql","-v","ON_ERROR_STOP=1","-v","demo_tenant_id="+DEMO_ID,
                "-U","test","-d","demo_seed_test","-f",SCRIPT).getExitCode();
    }
    private static String query(String sql) throws Exception {
        return POSTGRES.execInContainer("psql","-U","test","-d","demo_seed_test","-At","-c",sql).getStdout().trim();
    }
}
