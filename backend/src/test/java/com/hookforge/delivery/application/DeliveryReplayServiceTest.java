
package com.hookforge.delivery.application;

import com.hookforge.delivery.api.dto.DeliveryResponse;
import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryReplayServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    private DeliveryReplayService replayService;

    private UUID tenantId;
    private UUID deliveryId;

    @BeforeEach
    void setUp() {
        replayService = new DeliveryReplayService(deliveryRepository);

        tenantId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
    }

    @Test
    void shouldReplayFailedDelivery() {
        Delivery delivery = createFailedDelivery(3);
        stubDelivery(delivery);

        DeliveryResponse response =
                replayService.replay(tenantId, deliveryId);

        assertEquals(DeliveryStatus.PENDING, response.status());
        assertEquals(0, response.attemptCount());

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getProcessingToken());

        verify(deliveryRepository)
                .findByIdAndTenantIdForUpdate(deliveryId, tenantId);

        verify(deliveryRepository).save(delivery);
    }

    @Test
    void shouldReplayDeadDelivery() {
        Delivery delivery = createDeadDelivery();
        stubDelivery(delivery);

        DeliveryResponse response =
                replayService.replay(tenantId, deliveryId);

        assertEquals(DeliveryStatus.PENDING, response.status());
        assertEquals(0, response.attemptCount());

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount());

        verify(deliveryRepository).save(delivery);
    }

    @Test
    void shouldRejectReplayForSucceededDelivery() {
        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId
        );

        delivery.beginAttempt();
        delivery.markSucceeded();

        stubDelivery(delivery);

        assertThrows(
                DeliveryReplayNotAllowedException.class,
                () -> replayService.replay(tenantId, deliveryId)
        );

        assertEquals(DeliveryStatus.SUCCEEDED, delivery.getStatus());

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenDeliveryBelongsToAnotherTenant() {
        when(deliveryRepository.findByIdAndTenantIdForUpdate(
                deliveryId,
                tenantId
        )).thenReturn(Optional.empty());

        assertThrows(
                DeliveryNotFoundException.class,
                () -> replayService.replay(tenantId, deliveryId)
        );

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void shouldRejectReplayForPendingDelivery() {
        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId
        );

        stubDelivery(delivery);

        assertThrows(
                DeliveryReplayNotAllowedException.class,
                () -> replayService.replay(tenantId, deliveryId)
        );

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());

        verify(deliveryRepository, never()).save(any());
    }

    private Delivery createFailedDelivery(int attempts) {
        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId
        );

        for (int i = 0; i < attempts; i++) {
            delivery.beginAttempt();
        }

        delivery.markFailed();

        return delivery;
    }

    private Delivery createDeadDelivery() {
        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId
        );

        for (int i = 0; i < 5; i++) {
            delivery.beginAttempt();
        }

        delivery.markDead();

        return delivery;
    }

    private void stubDelivery(Delivery delivery) {
        when(deliveryRepository.findByIdAndTenantIdForUpdate(
                deliveryId,
                tenantId
        )).thenReturn(Optional.of(delivery));
    }
}
