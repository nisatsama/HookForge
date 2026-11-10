package com.hookforge.identity.application;

import com.hookforge.identity.domain.ApiKey;
import com.hookforge.identity.infrastructure.ApiKeyRepository;
import com.hookforge.tenant.domain.Tenant;
import com.hookforge.tenant.domain.TenantStatus;
import com.hookforge.tenant.infrastructure.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "hf_live_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            TenantRepository tenantRepository
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public CreatedApiKey create(
            UUID tenantId,
            String name,
            Instant expiresAt
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot create an API key for an inactive tenant");
        }

        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Expiration must be in the future");
        }

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);

        String secret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String rawKey = KEY_PREFIX + secret;
        String prefix = rawKey.substring(0, 16);
        String hash = sha256(rawKey);

        ApiKey apiKey = new ApiKey(
                tenant,
                name,
                prefix,
                hash,
                expiresAt
        );
        apiKeyRepository.save(apiKey);

        return new CreatedApiKey(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getCreatedAt(),
                apiKey.getExpiresAt()
        );
    }

    @Transactional
    public Optional<AuthenticatedTenant> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String hash = sha256(rawKey);

        return apiKeyRepository.findByKeyHash(hash)
                .filter(key -> key.isUsableAt(Instant.now()))
                .map(key -> {
                    key.markUsed();

                    return new AuthenticatedTenant(
                            key.getTenant().getId(),
                            key.getId()
                    );
                });
    }
    public record AuthenticatedTenant(
            UUID tenantId,
            UUID apiKeyId
    ) {
    }
    @Transactional
    public void revoke(UUID tenantId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository
                .findById(apiKeyId)
                .orElseThrow(() ->
                        new IllegalArgumentException("API key not found"));

        if (!apiKey.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("API key not found");
        }

        apiKey.revoke();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyMetadata> list(UUID tenantId) {
        return apiKeyRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenantId)
                .stream().map(key -> new ApiKeyMetadata(key.getId(), key.getName(),
                        key.getKeyPrefix(), key.getCreatedAt(), key.getExpiresAt(),
                        key.getRevokedAt(), key.getLastUsedAt()))
                .toList();
    }

    public record ApiKeyMetadata(UUID id, String name, String keyPrefix,
                                 Instant createdAt, Instant expiresAt,
                                 Instant revokedAt, Instant lastUsedAt) { }

    private static String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    public record CreatedApiKey(
            UUID id,
            String name,
            String keyPrefix,
            String secretKey,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
