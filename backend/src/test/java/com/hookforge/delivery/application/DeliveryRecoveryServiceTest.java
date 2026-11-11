
package com.hookforge.delivery.application;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryRecoveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;

    private DeliveryRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        long staleSeconds = 0;
        int batchSize = 0;
        recoveryService = new DeliveryRecoveryService(
                deliveryRepository,
                attemptRepository,
                staleSeconds,
                batchSize,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldRecoverStaleDeliveryAndScheduleRetry() {
        UUID deliveryId = UUID.randomUUID();

        Delivery delivery = createProcessingDelivery(2);

        DeliveryAttempt attempt = mock(DeliveryAttempt.class);
        Instant startedAt = Instant.now().minusSeconds(150);

        when(attempt.getStartedAt()).thenReturn(startedAt);

        when(deliveryRepository.findStaleProcessingDeliveryIds(120, 100))
                .thenReturn(List.of(deliveryId));

        when(deliveryRepository.findById(deliveryId))
                .thenReturn(Optional.of(delivery));

        when(attemptRepository.findByDeliveryIdAndCompletedAtIsNull(deliveryId))
                .thenReturn(Optional.of(attempt));

        recoveryService.recoverStaleDeliveries();

        assertEquals(DeliveryStatus.RETRYING, delivery.getStatus());
        assertNull(delivery.getProcessingToken());
        assertNotNull(delivery.getNextAttemptAt());

        // Attempt 2 uses 60 seconds of backoff.
        assertTrue(
                delivery.getNextAttemptAt().isAfter(
                        Instant.now().plusSeconds(50)
                )
        );

        verify(attempt).complete(
                isNull(),
                isNull(),
                eq("Worker lease expired before attempt completed"),
                longThat(duration -> duration >= 0)
        );

        verify(attemptRepository).save(attempt);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void shouldMarkDeliveryDeadWhenMaximumAttemptsReached() {
        UUID deliveryId = UUID.randomUUID();

        Delivery delivery = createProcessingDelivery(5);

        DeliveryAttempt attempt = mock(DeliveryAttempt.class);
        when(attempt.getStartedAt())
                .thenReturn(Instant.now().minusSeconds(150));

        when(deliveryRepository.findStaleProcessingDeliveryIds(120, 100))
                .thenReturn(List.of(deliveryId));

        when(deliveryRepository.findById(deliveryId))
                .thenReturn(Optional.of(delivery));

        when(attemptRepository.findByDeliveryIdAndCompletedAtIsNull(deliveryId))
                .thenReturn(Optional.of(attempt));

        recoveryService.recoverStaleDeliveries();

        assertEquals(DeliveryStatus.DEAD, delivery.getStatus());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getProcessingToken());

        verify(attempt).complete(
                isNull(),
                isNull(),
                eq("Worker lease expired before attempt completed"),
                anyLong()
        );

        verify(attemptRepository).save(attempt);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void shouldSkipDeliveryWhenDeliveryRecordNoLongerExists() {
        UUID deliveryId = UUID.randomUUID();

        when(deliveryRepository.findStaleProcessingDeliveryIds(120, 100))
                .thenReturn(List.of(deliveryId));

        when(deliveryRepository.findById(deliveryId))
                .thenReturn(Optional.empty());

        recoveryService.recoverStaleDeliveries();

        verifyNoInteractions(attemptRepository);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void shouldContinueRecoveryWhenIncompleteAttemptIsMissing() {
        UUID deliveryId = UUID.randomUUID();

        Delivery delivery = createProcessingDelivery(1);

        when(deliveryRepository.findStaleProcessingDeliveryIds(120, 100))
                .thenReturn(List.of(deliveryId));

        when(deliveryRepository.findById(deliveryId))
                .thenReturn(Optional.of(delivery));

        when(attemptRepository.findByDeliveryIdAndCompletedAtIsNull(deliveryId))
                .thenReturn(Optional.empty());

        recoveryService.recoverStaleDeliveries();

        assertEquals(DeliveryStatus.RETRYING, delivery.getStatus());

        verify(attemptRepository, never()).save(any());
        verify(deliveryRepository).save(delivery);
    }

    private Delivery createProcessingDelivery(int attemptCount) {
        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        for (int i = 0; i < attemptCount; i++) {
            delivery.beginAttempt();
        }

        delivery.assignProcessingToken(UUID.randomUUID());

        return delivery;
    }
}
