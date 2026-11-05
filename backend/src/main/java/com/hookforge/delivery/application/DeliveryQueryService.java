package com.hookforge.delivery.application;

import com.hookforge.delivery.api.dto.DeliveryAttemptResponse;
import com.hookforge.delivery.api.dto.DeliveryPageResponse;
import com.hookforge.delivery.api.dto.DeliveryResponse;
import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;

//import org.hibernate.query.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
@Service
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;

    public DeliveryQueryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(UUID tenantId, UUID deliveryId) {
        Delivery delivery = findTenantDelivery(tenantId, deliveryId);
        return DeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> getAttempts(
            UUID tenantId,
            UUID deliveryId
    ) {
        // Verify tenant ownership before retrieving attempt history.
        findTenantDelivery(tenantId, deliveryId);

        return attemptRepository
                .findAllByDeliveryIdOrderByStartedAtDesc(deliveryId)
                .stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
    }

    private Delivery findTenantDelivery(UUID tenantId, UUID deliveryId) {
        return deliveryRepository.findByIdAndTenantId(deliveryId, tenantId)
                .orElseThrow(() ->
                        new DeliveryNotFoundException(deliveryId));
    }
    @Transactional(readOnly = true)
    public DeliveryPageResponse getDeliveries(
            UUID tenantId,
            DeliveryStatus status,
            UUID eventId,
            UUID endpointId,
            int page,
            int size
    ) {
        Page<Delivery> deliveries =
                deliveryRepository.findAllByTenantWithFilters(
                        tenantId,
                        status,
                        eventId,
                        endpointId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "createdAt")
                        )
                );

        return new DeliveryPageResponse(
                deliveries.getContent()
                        .stream()
                        .map(DeliveryResponse::from)
                        .toList(),
                deliveries.getNumber(),
                deliveries.getSize(),
                deliveries.getTotalElements(),
                deliveries.getTotalPages(),
                deliveries.isLast()
        );
    }
}