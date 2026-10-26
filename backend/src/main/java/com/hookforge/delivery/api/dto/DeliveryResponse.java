package com.hookforge.delivery.api.dto;

import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID eventId,
        UUID endpointId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEndpointId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastAttemptAt(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}