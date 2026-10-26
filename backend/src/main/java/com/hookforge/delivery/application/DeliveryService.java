
package com.hookforge.delivery.application;

//import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.hookforge.webhook.WebhookHttpClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class DeliveryService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final WebhookEventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final WebhookHttpClient webhookHttpClient;
    private final ObjectMapper objectMapper;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            WebhookEventRepository eventRepository,
            EndpointRepository endpointRepository,
            WebhookHttpClient webhookHttpClient,
            ObjectMapper objectMapper
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.webhookHttpClient = webhookHttpClient;
        this.objectMapper = objectMapper;
    }
    @Transactional
    public boolean createIfAbsent(
            UUID eventId,
            UUID endpointId,
            UUID tenantId
    ) {
        if (deliveryRepository.existsByEventIdAndEndpointId(
                eventId,
                endpointId
        )) {
            return false;
        }

        Delivery delivery = new Delivery(
                eventId,
                endpointId,
                tenantId
        );

        deliveryRepository.save(delivery);
        return true;
    }
    @Transactional
    public void processDelivery(UUID deliveryId) {
    processDelivery(deliveryId,false);
    }
    public void processClaimedDelivery(UUID deliveryId){
        processDelivery(deliveryId,true);
    }
    private void processDelivery(UUID deliveryId,boolean alreadyClaimed){
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery not found: " + deliveryId
                ));


        if (delivery.getStatus() == DeliveryStatus.SUCCEEDED
                || delivery.getStatus() == DeliveryStatus.DEAD
                || delivery.getStatus() == DeliveryStatus.FAILED
                || (delivery.getStatus() == DeliveryStatus.PROCESSING
                && !alreadyClaimed)) {
            return;
        }


        if (delivery.getNextAttemptAt() != null
                && delivery.getNextAttemptAt().isAfter(Instant.now())) {
            return;
        }

        WebhookEvent event = eventRepository.findById(delivery.getEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Event not found: " + delivery.getEventId()
                ));

        WebhookEndpoint endpoint = endpointRepository
                .findByIdAndTenantId(
                        delivery.getEndpointId(),
                        delivery.getTenantId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Endpoint not found for delivery: " + deliveryId
                ));

        if (endpoint.getStatus() != WebhookEndpoint.Status.ACTIVE) {
            delivery.markFailed();
            deliveryRepository.save(delivery);
            return;
        }

        int attemptNumber = delivery.getAttemptCount() + 1;
        DeliveryAttempt attempt = new DeliveryAttempt(
                delivery.getId(),
                attemptNumber
        );

        delivery.beginAttempt();
        deliveryRepository.save(delivery);
        attemptRepository.save(attempt);

        long startedNanos = System.nanoTime();

        try {
            String body = objectMapper.writeValueAsString(event.getPayload());

            WebhookHttpClient.DeliveryResponse response =
                    webhookHttpClient.send(endpoint, body);

            attempt.complete(
                    response.statusCode(),
                    response.responseBody(),
                    null,
                    response.durationMs()
            );

            if (response.isSuccessful()) {
                delivery.markSucceeded();
            } else {
                handleFailure(delivery, attemptNumber);
            }

        } catch (IOException ex) {
            long durationMs = elapsedMillis(startedNanos);

            attempt.complete(
                    null,
                    null,
                    safeErrorMessage(ex),
                    durationMs
            );

            handleFailure(delivery, attemptNumber);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            attempt.complete(
                    null,
                    null,
                    "HTTP request interrupted",
                    elapsedMillis(startedNanos)
            );

            handleFailure(delivery, attemptNumber);

        } catch (RuntimeException ex) {
            attempt.complete(
                    null,
                    null,
                    safeErrorMessage(ex),
                    elapsedMillis(startedNanos)
            );

            handleFailure(delivery, attemptNumber);
        }

        attemptRepository.save(attempt);
        deliveryRepository.save(delivery);

    }

    private void handleFailure(Delivery delivery, int attemptNumber) {
        if (attemptNumber >= MAX_ATTEMPTS) {
            delivery.markDead();
            return;
        }

        long multiplier = 1L << Math.min(attemptNumber - 1, 20);
        long delay = Math.min(
                INITIAL_BACKOFF_SECONDS * multiplier,
                MAX_BACKOFF_SECONDS
        );

        delivery.scheduleRetry(Instant.now().plusSeconds(delay));
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        // Avoid persisting excessively long exception messages.
        return message.length() > 1000
                ? message.substring(0, 1000)
                : message;
    }
}
