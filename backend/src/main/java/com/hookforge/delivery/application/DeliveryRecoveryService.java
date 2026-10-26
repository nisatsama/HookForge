
package com.hookforge.delivery.application;

import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(DeliveryRecoveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final long staleSeconds;
    private final int batchSize;

    public DeliveryRecoveryService(
            DeliveryRepository deliveryRepository,
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
        this.staleSeconds = staleSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${hookforge.delivery.recovery-interval-ms:30000}"
    )
    @Transactional
    public void recoverStaleDeliveries() {
        int recovered =
                deliveryRepository.recoverStaleProcessingDeliveries(
                        staleSeconds,
                        batchSize
                );

        if (recovered > 0) {
            log.warn(
                    "Recovered {} stale deliveries from PROCESSING",
                    recovered
            );
        }
    }
}
