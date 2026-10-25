
package com.hookforge.delivery.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_delivery_attempts_number",
                columnNames = {"delivery_id", "attempt_number"}
        )
)
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(UUID deliveryId, int attemptNumber) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("deliveryId cannot be null");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }

        this.deliveryId = deliveryId;
        this.attemptNumber = attemptNumber;
        this.startedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void complete(
            Integer statusCode,
            String responseBody,
            String errorMessage,
            long durationMs
    ) {
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }

        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
