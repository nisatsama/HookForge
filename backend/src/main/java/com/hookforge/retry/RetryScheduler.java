
package com.hookforge.retry;

import com.hookforge.delivery.application.DeliveryClaimService;
import com.hookforge.delivery.application.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RetryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(RetryScheduler.class);

    private static final int BATCH_SIZE = 25;

    private final DeliveryClaimService deliveryClaimService;
    private final DeliveryService deliveryService;

    public RetryScheduler(
            DeliveryClaimService deliveryClaimService,
            DeliveryService deliveryService
    ) {
        this.deliveryClaimService = deliveryClaimService;
        this.deliveryService = deliveryService;
    }

    @Scheduled(
            fixedDelayString = "${hookforge.delivery.poll-interval-ms:1000}"
    )
    public void processDueDeliveries() {
        List<UUID> claimed;

        try {
            claimed = deliveryClaimService.claimReadyDeliveries(BATCH_SIZE);
        } catch (Exception ex) {
            log.error("Could not claim deliveries for processing", ex);
            return;
        }

        for (UUID deliveryId : claimed) {
            try {
                deliveryService.processClaimedDelivery(deliveryId);
            } catch (Exception ex) {
                log.error(
                        "Error processing claimed delivery {}",
                        deliveryId,
                        ex
                );
            }
        }
    }
}
