
package com.hookforge.delivery.application;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryCompletionServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository attemptRepository;
    @Mock
    private MeterRegistry meterRegistry;
    private DeliveryCompletionService completionService;

    @BeforeEach
    void setUp() {
        completionService = new DeliveryCompletionService(
                deliveryRepository,
                attemptRepository,
                meterRegistry
        );
    }

    @Test
    void shouldMarkDeliverySucceededForSuccessfulHttpResponse() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        completionService.complete(
                fixture.prepared(), 200, "OK", null, 150, false
        );

        assertEquals(DeliveryStatus.SUCCEEDED, fixture.delivery().getStatus());
        assertNotNull(fixture.attempt().getCompletedAt());
        assertEquals(200, fixture.attempt().getStatusCode());
        assertEquals("OK", fixture.attempt().getResponseBody());
        assertEquals(150, fixture.attempt().getDurationMs());

        verify(deliveryRepository).save(fixture.delivery());
        verify(attemptRepository).save(fixture.attempt());
    }

    @Test
    void shouldScheduleRetryForRateLimitedResponse() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        Instant before = Instant.now();

        completionService.complete(
                fixture.prepared(), 429, "Rate limited", null, 100, true
        );

        Instant nextAttemptAt = fixture.delivery().getNextAttemptAt();

        assertEquals(DeliveryStatus.RETRYING, fixture.delivery().getStatus());
        assertNotNull(nextAttemptAt);

        long delay = Duration.between(before, nextAttemptAt).getSeconds();

        assertTrue(delay >= 29 && delay <= 30,
                "First retry should be scheduled after approximately 30 seconds");

        assertNotNull(fixture.attempt().getCompletedAt());
        verify(deliveryRepository).save(fixture.delivery());
    }

    @Test
    void shouldMarkDeliveryFailedForPermanentHttpError() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        completionService.complete(
                fixture.prepared(), 400, "Bad request", null, 100, true
        );

        assertEquals(DeliveryStatus.FAILED, fixture.delivery().getStatus());
        assertEquals(
                "Webhook endpoint returned HTTP 400",
                fixture.attempt().getErrorMessage()
        );
        assertNull(fixture.delivery().getNextAttemptAt());
    }

    @Test
    void shouldMarkDeliveryDeadAfterFifthRetryableFailure() {
        TestFixture fixture = createFixture(5);
        stubAttempt(fixture);

        completionService.complete(
                fixture.prepared(), 500, "Internal server error", null, 200, true
        );

        assertEquals(DeliveryStatus.DEAD, fixture.delivery().getStatus());
        assertNull(fixture.delivery().getNextAttemptAt());
        assertNotNull(fixture.attempt().getCompletedAt());

        verify(deliveryRepository).save(fixture.delivery());
        verify(attemptRepository).save(fixture.attempt());
    }

    @Test
    void shouldApplyExponentialBackoff() {
        long[] expectedDelays = {30, 60, 120, 240};

        for (int attemptNumber = 1; attemptNumber <= 4; attemptNumber++) {
            TestFixture fixture = createFixture(attemptNumber);
            stubAttempt(fixture);

            Instant before = Instant.now();

            completionService.complete(
                    fixture.prepared(),
                    503,
                    "Service unavailable",
                    null,
                    100,
                    true
            );

            assertEquals(
                    DeliveryStatus.RETRYING,
                    fixture.delivery().getStatus()
            );

            long actualDelay = Duration.between(
                    before,
                    fixture.delivery().getNextAttemptAt()
            ).getSeconds();

            assertTrue(
                    actualDelay >= expectedDelays[attemptNumber - 1] - 1
                            && actualDelay <= expectedDelays[attemptNumber - 1],
                    "Incorrect backoff for attempt " + attemptNumber
            );
        }
    }

    @Test
    void shouldMarkDeliveryFailedForPermanentException() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        completionService.complete(
                fixture.prepared(),
                null,
                null,
                "Invalid destination",
                50,
                false
        );

        assertEquals(DeliveryStatus.FAILED, fixture.delivery().getStatus());
        assertEquals(
                "Invalid destination",
                fixture.attempt().getErrorMessage()
        );
    }

    @Test
    void shouldScheduleRetryForRetryableException() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        completionService.complete(
                fixture.prepared(),
                null,
                null,
                "Connection timed out",
                100,
                true
        );

        assertEquals(DeliveryStatus.RETRYING, fixture.delivery().getStatus());
        assertNotNull(fixture.delivery().getNextAttemptAt());
        assertEquals(
                "Connection timed out",
                fixture.attempt().getErrorMessage()
        );
    }

    @Test
    void shouldTruncateLongResponseAndClampNegativeDuration() {
        TestFixture fixture = createFixture(1);
        stubAttempt(fixture);

        String longResponse = "A".repeat(1500);

        completionService.complete(
                fixture.prepared(),
                200,
                longResponse,
                null,
                -10,
                false
        );

        assertEquals(1000, fixture.attempt().getResponseBody().length());
        assertEquals(0, fixture.attempt().getDurationMs());
    }

    @Test
    void shouldIgnoreCompletionFromStaleWorker() {
        TestFixture fixture = createFixture(1);

        PreparedDelivery stalePrepared = new PreparedDelivery(
                fixture.deliveryId(),
                UUID.randomUUID(),
                1,
                null,
                "{}"
        );

        completionService.complete(
                stalePrepared, 200, "OK", null, 100, false
        );

        assertEquals(
                DeliveryStatus.PROCESSING,
                fixture.delivery().getStatus()
        );

        assertNull(fixture.attempt().getCompletedAt());

        verifyNoInteractions(attemptRepository);
        verify(deliveryRepository, never()).save(any());
    }

    private TestFixture createFixture(int attemptNumber) {
        UUID deliveryId = UUID.randomUUID();
        UUID processingToken = UUID.randomUUID();

        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        for (int i = 0; i < attemptNumber; i++) {
            delivery.beginAttempt();
        }

        delivery.assignProcessingToken(processingToken);

        DeliveryAttempt attempt = new DeliveryAttempt(
                deliveryId,
                attemptNumber
        );

        PreparedDelivery prepared = new PreparedDelivery(
                deliveryId,
                processingToken,
                attemptNumber,
                null,
                "{}"
        );

        // Updated to match DeliveryCompletionService's pessimistic-lock lookup.
        when(deliveryRepository.findByIdForUpdate(deliveryId))
                .thenReturn(Optional.of(delivery));

        return new TestFixture(
                deliveryId,
                delivery,
                attempt,
                prepared
        );
    }

    private void stubAttempt(TestFixture fixture) {
        when(attemptRepository.findByDeliveryIdAndAttemptNumber(
                fixture.deliveryId(),
                fixture.prepared().attemptNumber()
        )).thenReturn(Optional.of(fixture.attempt()));
    }

    private record TestFixture(
            UUID deliveryId,
            Delivery delivery,
            DeliveryAttempt attempt,
            PreparedDelivery prepared
    ) {
    }
}
