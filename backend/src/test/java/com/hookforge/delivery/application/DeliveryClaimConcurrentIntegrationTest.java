
package com.hookforge.delivery.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "hookforge.outbox.poll-delay-ms=3600000",
        "hookforge.outbox.initial-delay-ms=3600000",
        "hookforge.delivery.poll-interval-ms=3600000",
        "hookforge.delivery.recovery-interval-ms=3600000"
})
@Testcontainers
class DeliveryClaimConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("hookforge_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DeliveryClaimService claimService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twoWorkersMustNotClaimTheSameDelivery() throws Exception {

        UUID tenantId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, slug)
                VALUES (?, 'Concurrency Test', ?)
                """, tenantId, "test-" + tenantId);

        jdbcTemplate.update("""
                INSERT INTO webhook_endpoints
                    (id, tenant_id, name, url, secret_hash)
                VALUES (?, ?, 'Test Endpoint',
                        'https://example.com/webhook', 'test-hash')
                """, endpointId, tenantId);

        jdbcTemplate.update("""
                INSERT INTO webhook_events
                    (id, tenant_id, event_type, payload)
                VALUES (?, ?, 'test.event', '{"test":true}'::jsonb)
                """, eventId, tenantId);

        jdbcTemplate.update("""
                INSERT INTO webhook_deliveries
                    (id, event_id, endpoint_id, tenant_id, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, deliveryId, eventId, endpointId, tenantId);

        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<UUID>> workerOne = executor.submit(() -> {
                startBarrier.await(10, TimeUnit.SECONDS);
                return claimService.claimReadyDeliveries(1);
            });

            Future<List<UUID>> workerTwo = executor.submit(() -> {
                startBarrier.await(10, TimeUnit.SECONDS);
                return claimService.claimReadyDeliveries(1);
            });

            List<UUID> firstClaims = workerOne.get(15, TimeUnit.SECONDS);
            List<UUID> secondClaims = workerTwo.get(15, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    firstClaims.size() + secondClaims.size(),
                    "Exactly one worker must claim the delivery"
            );

            assertEquals(
                    deliveryId,
                    firstClaims.isEmpty()
                            ? secondClaims.getFirst()
                            : firstClaims.getFirst()
            );

            assertEquals(
                    "PROCESSING",
                    jdbcTemplate.queryForObject(
                            "SELECT status FROM webhook_deliveries WHERE id = ?",
                            String.class,
                            deliveryId
                    )
            );

        } finally {
            executor.shutdownNow();
        }
    }
}
