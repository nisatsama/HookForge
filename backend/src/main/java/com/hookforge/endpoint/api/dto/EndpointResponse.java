package com.hookforge.endpoint.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hookforge.endpoint.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EndpointResponse(
        UUID id,
        String name,
        String url,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String secret
) {
    public static EndpointResponse from(WebhookEndpoint endpoint) {
        return from(endpoint, null);
    }

    public static EndpointResponse from(
            WebhookEndpoint endpoint,
            String secret
    ) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getStatus().name(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt(),
                secret
        );
    }
}