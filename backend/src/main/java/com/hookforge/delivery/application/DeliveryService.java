
package com.hookforge.delivery.application;

import com.hookforge.delivery.infrastructure.DeliveryRepository;
import com.hookforge.webhook.PermanentDestinationException;
import com.hookforge.webhook.RetryableDestinationException;
import com.hookforge.webhook.WebhookHttpClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPreparationService preparationService;
    private final DeliveryCompletionService completionService;
    private final WebhookHttpClient webhookHttpClient;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            DeliveryPreparationService preparationService,
            DeliveryCompletionService completionService,
            WebhookHttpClient webhookHttpClient
    ) {
        this.deliveryRepository = deliveryRepository;
        this.preparationService = preparationService;
        this.completionService = completionService;
        this.webhookHttpClient = webhookHttpClient;
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

    /**
     * Executes a claimed delivery.
     *
     * This method deliberately has no transaction:
     * preparation and completion run in separate transactions,
     * while the external HTTP request runs between them.
     */
    public void processClaimedDelivery(
            UUID deliveryId,
            UUID processingToken
    ) {
        PreparedDelivery prepared = preparationService
                .prepare(deliveryId, processingToken)
                .orElse(null);

        if (prepared == null) {
            return;
        }

        long startedNanos = System.nanoTime();

        try {
            WebhookHttpClient.DeliveryResponse response =
                    webhookHttpClient.send(
                            prepared.deliveryId(),
                            prepared.endpoint(),
                            prepared.requestBody()
                    );

            completionService.complete(
                    prepared,
                    response.statusCode(),
                    response.responseBody(),
                    null,
                    response.durationMs(),
                    false
            );

        } catch (InterruptedException ex) {
            completeFailure(
                    prepared,
                    "HTTP request interrupted",
                    startedNanos,
                    true
            );

            Thread.currentThread().interrupt();

        } catch (IOException | RetryableDestinationException ex) {
            completeFailure(
                    prepared,
                    safeErrorMessage(ex),
                    startedNanos,
                    true
            );

        } catch (IllegalArgumentException | IllegalStateException ex) {
            completeFailure(
                    prepared,
                    safeErrorMessage(ex),
                    startedNanos,
                    false
            );

        } catch (RuntimeException ex) {
            completeFailure(
                    prepared,
                    safeErrorMessage(ex),
                    startedNanos,
                    false
            );
        }
    }

    private void completeFailure(
            PreparedDelivery prepared,
            String errorMessage,
            long startedNanos,
            boolean retryable
    ) {
        long durationMs = Math.max(
                0,
                (System.nanoTime() - startedNanos) / 1_000_000
        );

        completionService.complete(
                prepared,
                null,
                null,
                errorMessage,
                durationMs,
                retryable
        );
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }

        int maxLength = 1000;

        return message.length() > maxLength
                ? message.substring(0, maxLength)
                : message;
    }
}
