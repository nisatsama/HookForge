
package com.hookforge.endpoint.application;

import com.hookforge.endpoint.api.dto.CreateEndpointRequest;
import com.hookforge.endpoint.api.dto.EndpointResponse;
import com.hookforge.endpoint.api.dto.UpdateEndpointRequest;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.security.WebhookSecretEncryptionService;
import com.hookforge.webhook.DestinationSecurityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final WebhookSecretEncryptionService encryptionService;
    private final DestinationSecurityService destinationSecurityService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EndpointService(
            EndpointRepository endpointRepository,
            WebhookSecretEncryptionService encryptionService,
            DestinationSecurityService destinationSecurityService
    ) {
        this.endpointRepository = endpointRepository;
        this.encryptionService = encryptionService;
        this.destinationSecurityService = destinationSecurityService;
    }

    @Transactional
    public EndpointResponse create(
            UUID tenantId,
            CreateEndpointRequest request
    ) {
        validateTenantId(tenantId);
        validateCreateRequest(request);

        String name = request.name().trim();
        String url = request.url().trim();

        validateName(name);
        validateUrl(url);

        String secret = generateSecret();
        String secretHash = hashSecret(secret);
        String encryptedSecret = encryptionService.encrypt(secret);

        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenantId,
                name,
                url,
                secretHash,
                encryptedSecret
        );

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        // Return the plaintext secret only during creation.
        return EndpointResponse.from(saved, secret);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> list(UUID tenantId) {
        validateTenantId(tenantId);

        return endpointRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(EndpointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EndpointResponse get(UUID tenantId, UUID id) {
        return EndpointResponse.from(findEndpoint(tenantId, id));
    }

    @Transactional
    public EndpointResponse update(
            UUID tenantId,
            UUID id,
            UpdateEndpointRequest request
    ) {
        validateUpdateRequest(request);

        WebhookEndpoint endpoint = findEndpoint(tenantId, id);

        String name = request.name().trim();
        String url = request.url().trim();

        validateName(name);
        validateUrl(url);

        endpoint.updateDetails(name, url);

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        return EndpointResponse.from(saved);
    }

    @Transactional
    public EndpointResponse disable(UUID tenantId, UUID id) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, id);

        endpoint.changeStatus(WebhookEndpoint.Status.DISABLED);

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        return EndpointResponse.from(saved);
    }

    @Transactional
    public EndpointResponse enable(UUID tenantId, UUID id) {
        WebhookEndpoint endpoint = findEndpoint(tenantId, id);

        // Revalidate the destination before enabling delivery.
        validateUrl(endpoint.getUrl());

        endpoint.changeStatus(WebhookEndpoint.Status.ACTIVE);

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        return EndpointResponse.from(saved);
    }

    private WebhookEndpoint findEndpoint(UUID tenantId, UUID id) {
        validateTenantId(tenantId);

        if (id == null) {
            throw new IllegalArgumentException(
                    "Endpoint ID is required"
            );
        }

        return endpointRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Endpoint not found")
                );
    }

    private void validateTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID is required"
            );
        }
    }

    private void validateCreateRequest(CreateEndpointRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Create endpoint request is required"
            );
        }

        validateNameInput(request.name());
        validateUrlInput(request.url());
    }

    private void validateUpdateRequest(UpdateEndpointRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Update endpoint request is required"
            );
        }

        validateNameInput(request.name());
        validateUrlInput(request.url());
    }

    private void validateNameInput(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Endpoint name is required"
            );
        }
    }

    private void validateUrlInput(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Endpoint URL is required"
            );
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException(
                    "Endpoint name must contain 1-150 characters"
            );
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Endpoint URL is required"
            );
        }

        destinationSecurityService.validateDestination(url);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return "whose_" +
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    secret.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }
}
