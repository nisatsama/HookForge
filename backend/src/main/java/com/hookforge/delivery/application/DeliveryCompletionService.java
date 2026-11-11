
package com.hookforge.delivery.application;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeliveryCompletionService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final long INITIAL_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final MeterRegistry meterRegistry;

    public DeliveryCompletionService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            MeterRegistry meterRegistry
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void complete(
            PreparedDelivery prepared,
            Integer statusCode,
            String responseBody,
            String errorMessage,
            long durationMs,
            boolean retryable
    ) {
        UUID deliveryId = prepared.deliveryId();

        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery not found: " + deliveryId
                ));

        if (delivery.getStatus() != DeliveryStatus.PROCESSING
                || !prepared.processingToken().equals(
                delivery.getProcessingToken()
        )) {
            return;
        }

        DeliveryAttempt attempt = attemptRepository
                .findByDeliveryIdAndAttemptNumber(
                        deliveryId,
                        prepared.attemptNumber()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Delivery attempt not found: "
                                + prepared.attemptNumber()
                ));

        if (attempt.getCompletedAt() != null) {
            return;
        }

        if (statusCode != null) {
            retryable = isRetryableHttpStatus(statusCode);

            if (statusCode < 200 || statusCode >= 300) {
                errorMessage = "Webhook endpoint returned HTTP "
                        + statusCode;
            }
        }

        attempt.complete(
                statusCode,
                truncate(responseBody),
                truncate(errorMessage),
                Math.max(0, durationMs)
        );

        attemptRepository.save(attempt);

        if (statusCode != null
                && statusCode >= 200
                && statusCode < 300) {

            delivery.markSucceeded();
            meterRegistry.counter("hookforge.deliveries.succeeded").increment();

        } else if (retryable) {

            int cycleAttemptNumber = delivery.getAttemptCount();

            if (cycleAttemptNumber >= MAX_ATTEMPTS) {
                delivery.markDead();
                meterRegistry.counter("hookforge.deliveries.failed").increment();
            } else {
                delivery.scheduleRetry(
                        Instant.now().plusSeconds(
                                calculateBackoff(cycleAttemptNumber)
                        )
                );
                meterRegistry.counter("hookforge.deliveries.retried").increment();
            }

        } else {
            delivery.markFailed();
            meterRegistry.counter("hookforge.deliveries.failed").increment();
        }

        deliveryRepository.save(delivery);
    }

    private boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode >= 500;
    }

    private long calculateBackoff(int attemptNumber) {
        long multiplier = 1L << Math.max(0, attemptNumber - 1);

        long delay = INITIAL_BACKOFF_SECONDS * multiplier;

        return Math.min(delay, MAX_BACKOFF_SECONDS);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }

        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}
