
package com.hookforge.endpoint.application;

import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.security.WebhookSecretEncryptionService;
import com.hookforge.webhook.DestinationSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
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
    public CreatedEndpoint createEndpoint(
            UUID tenantId,
            String name,
            String url
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }

        if (name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException(
                    "Endpoint name must contain 1–150 characters"
            );
        }

        destinationSecurityService.validateDestination(url);

        String secret = generateSecret();
        String secretHash = hashSecret(secret);
        String encryptedSecret = encryptionService.encrypt(secret);

        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenantId,
                name.trim(),
                url,
                secretHash,
                encryptedSecret
        );

        WebhookEndpoint saved = endpointRepository.save(endpoint);

        // Return the plaintext secret only once at creation.
        return new CreatedEndpoint(saved, secret);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return "whsec_" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    public record CreatedEndpoint(
            WebhookEndpoint endpoint,
            String secret
    ) {
    }
}
