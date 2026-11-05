package com.hookforge.delivery.application;

import com.hookforge.delivery.api.dto.DeliveryMetricsSummaryResponse;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeliveryMetricsService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryMetricsService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public DeliveryMetricsSummaryResponse getSummary(UUID tenantId) {

        return new DeliveryMetricsSummaryResponse(
                deliveryRepository.countByTenantId(tenantId),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.PENDING),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.PROCESSING),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.RETRYING),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.SUCCEEDED),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.FAILED),
                deliveryRepository.countByTenantIdAndStatus(
                        tenantId, DeliveryStatus.DEAD)
        );
    }
}