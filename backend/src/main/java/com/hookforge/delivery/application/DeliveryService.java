package com.hookforge.delivery.application;

import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * Creates a pending delivery unless one already exists
     * for this event and endpoint.
     *
     * @return true if a new delivery was inserted
     */
    @Transactional
    public boolean createIfAbsent(
            UUID eventId,
            UUID endpointId,
            UUID tenantId
    ) {
        return deliveryRepository.insertIfAbsent(
                eventId,
                endpointId,
                tenantId
        ) == 1;
    }
}