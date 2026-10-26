package com.hookforge.delivery.api.dto;

import com.hookforge.delivery.domain.DeliveryAttempt;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        int attemptNumber,
        Integer statusCode,
        String responseBody,
        String errorMessage,
        Long durationMs,
        Instant startedAt,
        Instant completedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getStatusCode(),
                attempt.getResponseBody(),
                attempt.getErrorMessage(),
                attempt.getDurationMs(),
                attempt.getStartedAt(),
                attempt.getCompletedAt()
        );
    }
}