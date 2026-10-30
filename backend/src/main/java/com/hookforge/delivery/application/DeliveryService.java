
package com.hookforge.delivery.application;

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

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

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
        return deliveryRepository.insertIfAbsent(
                eventId,
                endpointId,
                tenantId
        ) == 1;
    }

    @Transactional
    public void processDelivery(UUID deliveryId) {
        processDeliveryInternal(deliveryId, false);
    }

    @Transactional
    public void processClaimedDelivery(UUID deliveryId) {
        processDeliveryInternal(deliveryId, true);
    }

    private void processDeliveryInternal(
            UUID deliveryId,
            boolean alreadyClaimed
    ) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery not found: " + deliveryId
                ));

        if (isTerminal(delivery.getStatus())) {
            return;
        }

        if (delivery.getStatus() == DeliveryStatus.PROCESSING
                && !alreadyClaimed) {
            return;
        }

        Instant now = Instant.now();

        if (delivery.getNextAttemptAt() != null
                && delivery.getNextAttemptAt().isAfter(now)) {
            return;
        }

        // Never exceed the configured attempt limit.
        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.markDead();
            deliveryRepository.save(delivery);
            return;
        }

        WebhookEvent event = eventRepository.findById(
                delivery.getEventId()
        ).orElseThrow(() -> new IllegalStateException(
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

        int attemptNumber = delivery.getAttemptCount() + 1;

        DeliveryAttempt attempt = new DeliveryAttempt(
                delivery.getId(),
                attemptNumber
        );

        // Persist the attempt before performing the external HTTP request.
        delivery.beginAttempt();
        deliveryRepository.save(delivery);
        attemptRepository.save(attempt);

        long startedNanos = System.nanoTime();

        if (endpoint.getStatus() != WebhookEndpoint.Status.ACTIVE) {
            attempt.complete(
                    null,
                    null,
                    "Webhook endpoint is inactive",
                    elapsedMillis(startedNanos)
            );

            delivery.markFailed();

            attemptRepository.save(attempt);
            deliveryRepository.save(delivery);
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(
                    event.getPayload()
            );

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

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            attempt.complete(
                    null,
                    null,
                    "HTTP request interrupted",
                    elapsedMillis(startedNanos)
            );

            handleFailure(delivery, attemptNumber);

        } catch (IOException ex) {
            attempt.complete(
                    null,
                    null,
                    safeErrorMessage(ex),
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

    private boolean isTerminal(DeliveryStatus status) {
        return status == DeliveryStatus.SUCCEEDED
                || status == DeliveryStatus.DEAD
                || status == DeliveryStatus.FAILED;
    }

    private void handleFailure(
            Delivery delivery,
            int attemptNumber
    ) {
        if (attemptNumber >= MAX_ATTEMPTS) {
            delivery.markDead();
            return;
        }

        long multiplier = 1L << Math.min(attemptNumber - 1, 20);

        long delay = Math.min(
                INITIAL_BACKOFF_SECONDS * multiplier,
                MAX_BACKOFF_SECONDS
        );

        delivery.scheduleRetry(
                Instant.now().plusSeconds(delay)
        );
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }
}
