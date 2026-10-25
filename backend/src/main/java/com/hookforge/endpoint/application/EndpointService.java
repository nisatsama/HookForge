
package com.hookforge.endpoint.application;

import com.hookforge.endpoint.api.dto.CreateEndpointRequest;
import com.hookforge.endpoint.api.dto.EndpointResponse;
import com.hookforge.endpoint.api.dto.UpdateEndpointRequest;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public EndpointService(EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    public EndpointResponse create(
            UUID tenantId,
            CreateEndpointRequest request
    ) {
        validateUrl(request.url());

        String secretHash = generateSecretHash();

        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenantId,
                request.name().trim(),
                request.url().trim(),
                secretHash
        );

        return EndpointResponse.from(endpointRepository.save(endpoint));
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> list(UUID tenantId) {
        return endpointRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(EndpointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EndpointResponse get(UUID tenantId, UUID endpointId) {
        return EndpointResponse.from(findEndpoint(tenantId, endpointId));
    }

    public EndpointResponse update(
            UUID tenantId,
            UUID endpointId,
            UpdateEndpointRequest request
    ) {
        validateUrl(request.url());

        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpoint.updateDetails(
                request.name().trim(),
                request.url().trim()
        );

        return EndpointResponse.from(endpointRepository.save(endpoint));
    }

    public EndpointResponse disable(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpoint.changeStatus(WebhookEndpoint.Status.DISABLED);

        return EndpointResponse.from(endpointRepository.save(endpoint));
    }

    public EndpointResponse enable(UUID tenantId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, endpointId);
        endpoint.changeStatus(WebhookEndpoint.Status.ACTIVE);

        return EndpointResponse.from(endpointRepository.save(endpoint));
    }

    private WebhookEndpoint findEndpoint(UUID tenantId, UUID endpointId) {
        return endpointRepository.findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new EndpointNotFoundException(endpointId));
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value.trim());

            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("https")
                    || scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "URL must be an absolute HTTP or HTTPS URL"
                );
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid endpoint URL: provide a valid HTTP or HTTPS URL",
                    ex
            );
        }
    }

    private String generateSecretHash() {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static class EndpointNotFoundException extends RuntimeException {
        public EndpointNotFoundException(UUID endpointId) {
            super("Endpoint not found: " + endpointId);
        }
    }
}
