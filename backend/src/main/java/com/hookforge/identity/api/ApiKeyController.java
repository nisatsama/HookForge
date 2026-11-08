
package com.hookforge.identity.api;

import com.hookforge.identity.application.ApiKeyService;
import com.hookforge.security.AuthenticatedTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKeyService.ApiKeyMetadata> list(
            @AuthenticationPrincipal AuthenticatedTenant principal
    ) {
        return apiKeyService.list(principal.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyService.CreatedApiKey create(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        return apiKeyService.create(
                principal.tenantId(),
                request.name(),
                request.expiresAt()
        );
    }

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @PathVariable UUID id
    ) {
        apiKeyService.revoke(principal.tenantId(), id);
    }

    public record CreateApiKeyRequest(
            @NotBlank
            @Size(max = 100)
            String name,
            Instant expiresAt
    ) {
    }
}
