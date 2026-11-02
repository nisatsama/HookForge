
package com.hookforge.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.domain.OutboxStatus;
import com.hookforge.outbox.infrastructure.OutboxEventRepository;
import com.hookforge.tenant.domain.Tenant;
import com.hookforge.tenant.infrastructure.TenantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "hookforge.outbox.poll-delay-ms=3600000",
        "hookforge.outbox.initial-delay-ms=3600000"
})
@Testcontainers
class OutboxPublisherIntegrationTest {

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
    private OutboxPublisher outboxPublisher;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private WebhookEventRepository eventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() throws Exception {
        outboxEventRepository.deleteAll();
        eventRepository.deleteAll();
        tenantRepository.deleteAll();

        reset(kafkaTemplate);

        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant("HookForge Test", "hookforge-test")
        );

        ObjectMapper mapper = new ObjectMapper();

        JsonNode payload = mapper.readTree("""
                {
                    "orderId": "ORD-1001",
                    "amount": 2499
                }
                """);

        WebhookEvent event = eventRepository.saveAndFlush(
                new WebhookEvent(
                        tenant.getId(),
                        "order.created",
                        null,
                        null,
                        payload
                )
        );

        outboxEvent = outboxEventRepository.saveAndFlush(
                new OutboxEvent(
                        event.getId(),
                        event.getEventType(),
                        payload
                )
        );
    }

    @Test
    void shouldRecoverAndPublishPendingEventAfterKafkaOutage() {

        // First attempt: Kafka is unavailable.
        CompletableFuture<SendResult<String, String>> failedFuture =
                CompletableFuture.failedFuture(
                        new RuntimeException("Kafka unavailable")
                );

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(failedFuture);

        int firstPublishedCount = outboxPublisher.publishPendingEvents();

        assertEquals(0, firstPublishedCount);

        OutboxEvent afterFailure = outboxEventRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertEquals(OutboxStatus.PENDING, afterFailure.getStatus());
        assertEquals(1, afterFailure.getAttempts());
        assertNull(afterFailure.getPublishedAt());

        // Second attempt: Kafka has recovered.
        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult =
                mock(SendResult.class);

        CompletableFuture<SendResult<String, String>> successfulFuture =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(successfulFuture);

        int secondPublishedCount = outboxPublisher.publishPendingEvents();

        assertEquals(1, secondPublishedCount);

        OutboxEvent afterRecovery = outboxEventRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertEquals(OutboxStatus.PUBLISHED, afterRecovery.getStatus());
        assertEquals(1, afterRecovery.getAttempts());
        assertNotNull(afterRecovery.getPublishedAt());

        verify(kafkaTemplate, times(2)).send(
                anyString(),
                anyString(),
                anyString()
        );
    }
}
