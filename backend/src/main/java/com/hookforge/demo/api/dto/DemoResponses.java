
package com.hookforge.demo.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DemoResponses {

    private DemoResponses() {}

    public record OverviewResponse(
            MetricsResponse metrics,
            List<DeliveryResponse> recentDeliveries
    ) {}

    public record MetricsResponse(
            long total,
            long pending,
            long processing,
            long retrying,
            long succeeded,
            long failed,
            long dead
    ) {}

    public record EndpointResponse(
            String id,
            String name,
            String url,
            String status,
            Instant createdAt
    ) {}

    public record EventResponse(
            String id,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt,
            boolean created
    ) {}

    public record DeliveryResponse(
            String id,
            String eventId,
            String endpointId,
            String endpointName,
            String eventType,
            String status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant lastAttemptAt,
            Instant deliveredAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AttemptResponse(
            String id,
            int attemptNumber,
            Integer statusCode,
            String responseBody,
            String errorMessage,
            Long durationMs,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {}
}
