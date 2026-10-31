
package com.hookforge.delivery.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookforge.delivery.domain.Delivery;
import com.hookforge.delivery.domain.DeliveryAttempt;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.delivery.infrastructure.DeliveryAttemptRepository;
import com.hookforge.delivery.infrastructure.DeliveryRepository;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryPreparationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final WebhookEventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;

    public DeliveryPreparationService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            WebhookEventRepository eventRepository,
            EndpointRepository endpointRepository,
            ObjectMapper objectMapper
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<PreparedDelivery> prepare(
            UUID deliveryId,
            UUID processingToken
    ) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery not found: " + deliveryId
                ));

        if (delivery.getStatus() != DeliveryStatus.PROCESSING
                || !processingToken.equals(delivery.getProcessingToken())) {
            return Optional.empty();
        }

        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.markDead();
            deliveryRepository.save(delivery);
            return Optional.empty();
        }

        int attemptNumber = delivery.getAttemptCount() + 1;

        DeliveryAttempt attempt = new DeliveryAttempt(
                delivery.getId(),
                attemptNumber
        );

        delivery.beginAttempt();

        deliveryRepository.save(delivery);
        attemptRepository.save(attempt);

        WebhookEvent event = eventRepository.findById(delivery.getEventId())
                .orElse(null);

        if (event == null) {
            completePermanentFailure(
                    delivery,
                    attempt,
                    "Webhook event not found"
            );
            return Optional.empty();
        }

        WebhookEndpoint endpoint = endpointRepository
                .findByIdAndTenantId(
                        delivery.getEndpointId(),
                        delivery.getTenantId()
                )
                .orElse(null);

        if (endpoint == null) {
            completePermanentFailure(
                    delivery,
                    attempt,
                    "Webhook endpoint not found"
            );
            return Optional.empty();
        }

        if (endpoint.getStatus() != WebhookEndpoint.Status.ACTIVE) {
            completePermanentFailure(
                    delivery,
                    attempt,
                    "Webhook endpoint is inactive"
            );
            return Optional.empty();
        }

        try {
            String requestBody = objectMapper.writeValueAsString(
                    event.getPayload()
            );

            deliveryRepository.flush();
            attemptRepository.flush();

            return Optional.of(new PreparedDelivery(
                    delivery.getId(),
                    processingToken,
                    attemptNumber,
                    endpoint,
                    requestBody
            ));

        } catch (JsonProcessingException ex) {
            completePermanentFailure(
                    delivery,
                    attempt,
                    "Could not serialize webhook payload: "
                            + ex.getOriginalMessage()
            );
            return Optional.empty();
        }
    }

    private void completePermanentFailure(
            Delivery delivery,
            DeliveryAttempt attempt,
            String errorMessage
    ) {
        attempt.complete(
                null,
                null,
                truncate(errorMessage),
                0
        );

        delivery.markFailed();

        attemptRepository.save(attempt);
        deliveryRepository.save(delivery);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown preparation error";
        }

        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
