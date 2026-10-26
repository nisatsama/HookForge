
package com.hookforge.delivery.application;

import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryClaimService {

    private static final int MAX_BATCH_SIZE = 100;

    private final DeliveryRepository deliveryRepository;

    public DeliveryClaimService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public List<UUID> claimReadyDeliveries(int requestedBatchSize) {
        if (requestedBatchSize < 1) {
            throw new IllegalArgumentException(
                    "Batch size must be positive"
            );
        }

        int batchSize = Math.min(requestedBatchSize, MAX_BATCH_SIZE);

        List<UUID> candidates =
                deliveryRepository.findReadyDeliveryIds(batchSize);

        List<UUID> claimed = new ArrayList<>();

        for (UUID id : candidates) {
            if (deliveryRepository.claimDelivery(id) == 1) {
                claimed.add(id);
            }
        }

        return claimed;
    }
}
