
package com.hookforge.delivery.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import com.hookforge.tenant.infrastructure.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryPreparationServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;

    @Mock
    private WebhookEventRepository eventRepository;

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private TenantRepository tenantRepository;

    private ObjectMapper objectMapper;
    private DeliveryPreparationService preparationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        preparationService = new DeliveryPreparationService(
                deliveryRepository,
                attemptRepository,
                eventRepository,
                endpointRepository,
                objectMapper,
                tenantRepository
        );
    }

    @Test
    void demoTenantDeliveryIsNeverPreparedForHttpDispatch() {
        UUID tenantId=UUID.randomUUID(), deliveryId=UUID.randomUUID();
        Delivery delivery=mock(Delivery.class);
        when(deliveryRepository.findByIdForUpdate(deliveryId)).thenReturn(Optional.of(delivery));
        when(delivery.getStatus()).thenReturn(DeliveryStatus.PROCESSING);
        when(delivery.getProcessingToken()).thenReturn(UUID.randomUUID());
        when(delivery.getTenantId()).thenReturn(tenantId);
        when(tenantRepository.existsByIdAndDemoTrue(tenantId)).thenReturn(true);

        assertTrue(preparationService.prepare(deliveryId,delivery.getProcessingToken()).isEmpty());
        verifyNoInteractions(eventRepository,endpointRepository,attemptRepository);
    }

    @Test
    void replayedDeliveryShouldUseNextGlobalAttemptNumber()
            throws Exception {

        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID processingToken = UUID.randomUUID();

        Delivery delivery = new Delivery(
                eventId,
                endpointId,
                tenantId
        );

        ReflectionTestUtils.setField(delivery, "id", deliveryId);

        // Simulate five previous attempts followed by a replay.
        delivery.markFailed();
        delivery.replay();

        // Simulate the worker claiming the replayed delivery.
        ReflectionTestUtils.setField(
                delivery,
                "status",
                DeliveryStatus.PROCESSING
        );
        delivery.assignProcessingToken(processingToken);

        WebhookEvent event = new WebhookEvent(
                tenantId,
                "order.created",
                null,
                null,
                objectMapper.readTree("{\"orderId\":123}")
        );

        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenantId,
                "Test Endpoint",
                "https://example.com/webhook",
                "test-secret"
        );

        when(deliveryRepository.findByIdForUpdate(deliveryId))
                .thenReturn(Optional.of(delivery));

        when(attemptRepository.findMaxAttemptNumberByDeliveryId(deliveryId))
                .thenReturn(5);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(event));

        when(endpointRepository.findByIdAndTenantId(
                endpointId,
                tenantId
        )).thenReturn(Optional.of(endpoint));

        Optional<PreparedDelivery> result =
                preparationService.prepare(deliveryId, processingToken);

        assertTrue(result.isPresent());

        ArgumentCaptor<DeliveryAttempt> attemptCaptor =
                ArgumentCaptor.forClass(DeliveryAttempt.class);

        verify(attemptRepository).save(attemptCaptor.capture());

        // Historical attempts are numbered 1–5.
        // The replay must create attempt number 6.
        assertEquals(
                6,
                attemptCaptor.getValue().getAttemptNumber()
        );

        // The new replay cycle starts at attempt 1.
        assertEquals(1, delivery.getAttemptCount());

        assertEquals(
                DeliveryStatus.PROCESSING,
                delivery.getStatus()
        );

        verify(deliveryRepository).save(delivery);
        verify(deliveryRepository).flush();
        verify(attemptRepository).flush();
    }
}
