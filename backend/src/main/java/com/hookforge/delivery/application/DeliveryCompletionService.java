package com.hookforge.delivery.application;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
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

    public DeliveryCompletionService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
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

        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery not found: " + deliveryId
                ));

        // Ignore stale workers whose processing lease has expired.
        if (delivery.getStatus() != Delivery.DeliveryStatus.PROCESSING
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
                        "Delivery attempt not found"
                ));

        if (attempt.getCompletedAt() != null) {
            return;
        }

        String finalError = errorMessage;

        if (statusCode != null && (statusCode < 200 || statusCode >= 300)) {
            retryable = isRetryableHttpStatus(statusCode);

            if (finalError == null) {
                finalError = "Webhook endpoint returned HTTP "
                        + statusCode;
            }
        }

        attempt.complete(
                statusCode,
                truncate(responseBody, MAX_ERROR_LENGTH),
                truncate(finalError, MAX_ERROR_LENGTH),
                Math.max(0, durationMs)
        );

        attemptRepository.save(attempt);

        if (statusCode != null && statusCode >= 200 && statusCode < 300) {
            delivery.markSucceeded();
        } else if (retryable) {
            if (prepared.attemptNumber() >= MAX_ATTEMPTS) {
                delivery.markDead();
            } else {
                delivery.scheduleRetry(
                        Instant.now().plusSeconds(
                                calculateBackoff(prepared.attemptNumber())
                        )
                );
            }
        } else {
            delivery.markFailed();
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
        long backoff = INITIAL_BACKOFF_SECONDS
                * (1L << Math.max(0, attemptNumber - 1));

        return Math.min(backoff, MAX_BACKOFF_SECONDS);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}