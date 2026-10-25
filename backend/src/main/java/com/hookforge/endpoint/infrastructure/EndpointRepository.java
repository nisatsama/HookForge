
package com.hookforge.endpoint.infrastructure;

import com.hookforge.endpoint.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndpointRepository
        extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findAllByTenantIdOrderByCreatedAtDesc(
            UUID tenantId
    );

    Optional<WebhookEndpoint> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );
}
