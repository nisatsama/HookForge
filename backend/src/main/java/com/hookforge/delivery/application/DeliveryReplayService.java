
package com.hookforge.delivery.application;

import com.hookforge.delivery.api.dto.DeliveryResponse;
import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeliveryReplayService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryReplayService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public DeliveryResponse replay(UUID tenantId, UUID deliveryId) {

        Delivery delivery = deliveryRepository
                .findByIdAndTenantIdForUpdate(deliveryId, tenantId)
                .orElseThrow(() ->
                        new DeliveryNotFoundException(deliveryId)
                );

        try {
            delivery.replay();
        } catch (IllegalStateException ex) {
            throw new DeliveryReplayNotAllowedException(
                    deliveryId,
                    delivery.getStatus().name()
            );
        }

        deliveryRepository.save(delivery);

        return DeliveryResponse.from(delivery);
    }
}
