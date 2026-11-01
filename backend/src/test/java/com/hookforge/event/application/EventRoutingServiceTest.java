
package com.hookforge.event.application;

import com.hookforge.delivery.application.DeliveryService;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRoutingServiceTest {

    @Mock
    private WebhookEventRepository eventRepository;

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private EventRoutingService eventRoutingService;

    private UUID eventId;
    private UUID tenantId;
    private WebhookEvent event;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        event = mock(WebhookEvent.class);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(event));
    }

    @Test
    void shouldRouteOnlyToActiveEndpoints() {

        when(event.getId()).thenReturn(eventId);
        when(event.getTenantId()).thenReturn(tenantId);

        WebhookEndpoint activeEndpoint = createEndpoint(
                UUID.randomUUID(),
                WebhookEndpoint.Status.ACTIVE
        );

        WebhookEndpoint disabledEndpoint = createEndpoint(
                UUID.randomUUID(),
                WebhookEndpoint.Status.DISABLED
        );

        when(endpointRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(activeEndpoint, disabledEndpoint));

        when(deliveryService.createIfAbsent(
                eventId,
                activeEndpoint.getId(),
                tenantId
        )).thenReturn(true);

        int createdCount = eventRoutingService.routeEvent(eventId);

        assertEquals(1, createdCount);

        verify(deliveryService).createIfAbsent(
                eventId,
                activeEndpoint.getId(),
                tenantId
        );

        verify(deliveryService, never()).createIfAbsent(
                eventId,
                disabledEndpoint.getId(),
                tenantId
        );
    }

    @Test
    void shouldUseEventsTenantIdToFindEndpoints() {

        when(event.getTenantId()).thenReturn(tenantId);

        when(endpointRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of());

        int createdCount = eventRoutingService.routeEvent(eventId);

        assertEquals(0, createdCount);

        verify(endpointRepository)
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        verifyNoInteractions(deliveryService);
    }

    @Test
    void shouldNotCountExistingDeliveriesAsNew() {

        when(event.getId()).thenReturn(eventId);
        when(event.getTenantId()).thenReturn(tenantId);

        WebhookEndpoint firstEndpoint = createEndpoint(
                UUID.randomUUID(),
                WebhookEndpoint.Status.ACTIVE
        );

        WebhookEndpoint secondEndpoint = createEndpoint(
                UUID.randomUUID(),
                WebhookEndpoint.Status.ACTIVE
        );

        when(endpointRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(firstEndpoint, secondEndpoint));

        when(deliveryService.createIfAbsent(
                eventId,
                firstEndpoint.getId(),
                tenantId
        )).thenReturn(true);

        when(deliveryService.createIfAbsent(
                eventId,
                secondEndpoint.getId(),
                tenantId
        )).thenReturn(false);

        int createdCount = eventRoutingService.routeEvent(eventId);

        assertEquals(1, createdCount);

        verify(deliveryService, times(2))
                .createIfAbsent(any(), any(), any());
    }

    @Test
    void shouldThrowExceptionWhenEventDoesNotExist() {

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> eventRoutingService.routeEvent(eventId)
        );

        verifyNoInteractions(endpointRepository);
        verifyNoInteractions(deliveryService);
    }

    private WebhookEndpoint createEndpoint(
            UUID endpointId,
            WebhookEndpoint.Status status
    ) {
        WebhookEndpoint endpoint = mock(WebhookEndpoint.class);

        when(endpoint.getId()).thenReturn(endpointId);
        when(endpoint.getStatus()).thenReturn(status);

        return endpoint;
    }
}
