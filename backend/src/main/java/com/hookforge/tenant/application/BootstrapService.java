
package com.hookforge.tenant.application;

import com.hookforge.identity.application.ApiKeyService;
import com.hookforge.tenant.api.dto.CreateTenantRequest;
import com.hookforge.tenant.api.dto.TenantResponse;
import com.hookforge.tenant.domain.BootstrapState;
import com.hookforge.tenant.infrastructure.BootstrapStateRepository;
import com.hookforge.tenant.infrastructure.TenantRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class BootstrapService {

    private final BootstrapStateRepository bootstrapStateRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;
    private final String bootstrapSecret;

    public BootstrapService(
            BootstrapStateRepository bootstrapStateRepository,
            TenantRepository tenantRepository,
            TenantService tenantService,
            ApiKeyService apiKeyService,
            @Value("${hookforge.bootstrap.secret:}") String bootstrapSecret
    ) {
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.apiKeyService = apiKeyService;
        this.bootstrapSecret = bootstrapSecret;
    }

    @Transactional
    public BootstrapResult bootstrap(
            String suppliedSecret,
            CreateTenantRequest request
    ) {
        if (bootstrapSecret.isBlank()) {
            throw new BootstrapDisabledException();
        }

        if (suppliedSecret == null
                || !MessageDigest.isEqual(
                        bootstrapSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8)
                )) {
            throw new InvalidBootstrapSecretException();
        }

        BootstrapState state = bootstrapStateRepository
                .findByIdForUpdate((short) 1)
                .orElseThrow(BootstrapDisabledException::new);

        if (state.isInitialized() || tenantRepository.count() > 0) {
            throw new BootstrapAlreadyCompletedException();
        }

        TenantResponse tenant = tenantService.create(request);

        ApiKeyService.CreatedApiKey apiKey = apiKeyService.create(
                tenant.id(),
                "Initial bootstrap key",
                null
        );

        state.markInitialized();

        return new BootstrapResult(tenant, apiKey);
    }

    public record BootstrapResult(
            TenantResponse tenant,
            ApiKeyService.CreatedApiKey apiKey
    ) {
    }

    public static class BootstrapDisabledException
            extends RuntimeException {
    }

    public static class InvalidBootstrapSecretException
            extends RuntimeException {
    }

    public static class BootstrapAlreadyCompletedException
            extends RuntimeException {
    }
}
