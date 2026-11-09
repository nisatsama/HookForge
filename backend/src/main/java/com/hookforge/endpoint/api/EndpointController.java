
package com.hookforge.endpoint.api;

import com.hookforge.endpoint.api.dto.CreateEndpointRequest;
import com.hookforge.endpoint.api.dto.EndpointResponse;
import com.hookforge.endpoint.api.dto.UpdateEndpointRequest;
import com.hookforge.endpoint.application.EndpointService;
import com.hookforge.security.AuthenticatedTenant;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {

    private static final Logger log =
            LoggerFactory.getLogger(EndpointController.class);

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointResponse create(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @Valid @RequestBody CreateEndpointRequest request
    ) {
        return endpointService.create(
                requireTenant(principal),
                request
        );
    }

    @GetMapping
    public List<EndpointResponse> list(
            @AuthenticationPrincipal AuthenticatedTenant principal
    ) {
        log.warn("[AuthDiag] endpoint_list injected_principal_null={} injected_principal_tenant_id_null={}",
                principal == null, principal == null || principal.tenantId() == null);
        return endpointService.list(requireTenant(principal));
    }

    @GetMapping("/{id}")
    public EndpointResponse get(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @PathVariable UUID id
    ) {
        return endpointService.get(requireTenant(principal), id);
    }

    @PutMapping("/{id}")
    public EndpointResponse update(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEndpointRequest request
    ) {
        return endpointService.update(
                requireTenant(principal),
                id,
                request
        );
    }

    @PatchMapping("/{id}/disable")
    public EndpointResponse disable(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @PathVariable UUID id
    ) {
        return endpointService.disable(requireTenant(principal), id);
    }

    @PatchMapping("/{id}/enable")
    public EndpointResponse enable(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @PathVariable UUID id
    ) {
        return endpointService.enable(requireTenant(principal), id);
    }

    private UUID requireTenant(AuthenticatedTenant principal) {
        if (principal == null || principal.tenantId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated tenant is required"
            );
        }

        return principal.tenantId();
    }
}
