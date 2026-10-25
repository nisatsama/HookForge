
package com.hookforge.endpoint.api.dto;

import com.hookforge.endpoint.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.UUID;

public record EndpointResponse(
        UUID id,
        String name,
        String url,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static EndpointResponse from(WebhookEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getStatus().name(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}
