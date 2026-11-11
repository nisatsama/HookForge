
package com.hookforge.delivery.application;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(DeliveryRecoveryService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final MeterRegistry meterRegistry;
    private final long staleSeconds;
    private final int batchSize;

    public DeliveryRecoveryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            @Value("${hookforge.delivery.recovery-stale-seconds:120}")
            long staleSeconds,
            @Value("${hookforge.delivery.recovery-batch-size:100}")
            int batchSize
    ) {
        if (staleSeconds < 30) {
            throw new IllegalArgumentException(
                    "Recovery stale timeout must be at least 30 seconds"
            );
        }

        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "Recovery batch size must be between 1 and 1000"
            );
        }

        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.staleSeconds = staleSeconds;
        this.batchSize = batchSize;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            fixedDelayString =
                    "${hookforge.delivery.recovery-interval-ms:30000}"
    )
    @Transactional
    public void recoverStaleDeliveries() {

        List<UUID> staleDeliveryIds =
                deliveryRepository.findStaleProcessingDeliveryIds(
                        staleSeconds,
                        batchSize
                );

        int recovered = 0;

        for (UUID deliveryId : staleDeliveryIds) {

            Delivery delivery = deliveryRepository.findById(deliveryId)
                    .orElse(null);

            if (delivery == null) {
                continue;
            }

            Instant now = Instant.now();

            DeliveryAttempt attempt = attemptRepository
                    .findByDeliveryIdAndCompletedAtIsNull(deliveryId)
                    .orElse(null);

            if (attempt != null) {
                long durationMs = Math.max(
                        0,
                        Duration.between(
                                attempt.getStartedAt(),
                                now
                        ).toMillis()
                );

                attempt.complete(
                        null,
                        null,
                        "Worker lease expired before attempt completed",
                        durationMs
                );

                attemptRepository.save(attempt);
            } else {
                log.error(
                        "Stale delivery {} has no incomplete attempt record",
                        deliveryId
                );
            }

            if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
                delivery.markDead();
            } else {
                delivery.scheduleRetry(
                        now.plusSeconds(calculateBackoff(
                                delivery.getAttemptCount()
                        ))
                );
            }

            deliveryRepository.save(delivery);
            recovered++;
        }

        if (recovered > 0) {
            log.warn(
                    "Recovered {} stale deliveries from PROCESSING",
                    recovered
            );
        }
    }

    private long calculateBackoff(int attemptNumber) {
        long multiplier =
                1L << Math.min(Math.max(attemptNumber - 1, 0), 20);

        return Math.min(
                INITIAL_BACKOFF_SECONDS * multiplier,
                MAX_BACKOFF_SECONDS
        );
    }
}
