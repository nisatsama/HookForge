
package com.hookforge.endpoint.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    public enum Status {
        ACTIVE,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    @Column(name = "secret_encrypted", columnDefinition = "TEXT")
    private String secretEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(
            UUID tenantId,
            String name,
            String url,
            String secretHash
    ) {
        this(tenantId, name, url, secretHash, null);
    }

    public WebhookEndpoint(
            UUID tenantId,
            String name,
            String url,
            String secretHash,
            String secretEncrypted
    ) {
        this.tenantId = tenantId;
        this.name = name;
        this.url = url;
        this.secretHash = secretHash;
        this.secretEncrypted = secretEncrypted;
        this.status = Status.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateDetails(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public void changeStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
