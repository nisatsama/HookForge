
package com.hookforge.delivery.application;

import com.hookforge.endpoint.domain.WebhookEndpoint;

import java.util.UUID;

public record PreparedDelivery(
        UUID deliveryId,
        UUID processingToken,
        int attemptNumber,
        WebhookEndpoint endpoint,
        String requestBody
) {
}
