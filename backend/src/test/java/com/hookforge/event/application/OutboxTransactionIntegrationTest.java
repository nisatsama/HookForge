
package com.hookforge.event.application;

import com.hookforge.event.api.dto.CreateEventRequest;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.outbox.domain.OutboxEvent;
import com.hookforge.outbox.infrastructure.OutboxEventRepository;
import com.hookforge.security.AuthenticatedTenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class OutboxTransactionIntegrationTest {

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
    private EventService eventService;

    @Autowired
    private WebhookEventRepository eventRepository;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRollbackWebhookEventWhenOutboxInsertFails() throws Exception {

        // Arrange: create an authenticated tenant.
        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();

        AuthenticatedTenant principal =
                new AuthenticatedTenant(tenantId, apiKeyId);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_TENANT"))
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        CreateEventRequest request = new CreateEventRequest(
                "order.created",
                objectMapper.readTree("""
                        {
                            "orderId": "ORD-1001",
                            "amount": 2499
                        }
                        """)
        );

        // Force the outbox save to fail.
        doThrow(new DataIntegrityViolationException(
                "Simulated outbox insertion failure"
        )).when(outboxRepository).save(any(OutboxEvent.class));

        // Act + Assert: EventService must propagate the failure.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> eventService.createEvent(request, null)
        );

        // Assert: neither database record should have committed.
        assertEquals(
                0L,
                eventRepository.count(),
                "Webhook event must be rolled back"
        );

        assertEquals(
                0L,
                outboxRepository.count(),
                "Outbox record must not exist"
        );
    }
}
