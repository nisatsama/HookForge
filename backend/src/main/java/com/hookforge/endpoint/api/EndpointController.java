
package com.hookforge.endpoint.api;

import com.hookforge.endpoint.api.dto.CreateEndpointRequest;
import com.hookforge.endpoint.api.dto.EndpointResponse;
import com.hookforge.endpoint.api.dto.UpdateEndpointRequest;
import com.hookforge.endpoint.application.EndpointService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointResponse create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateEndpointRequest request
    ) {
        return endpointService.create(tenantId, request);
    }

    @GetMapping
    public List<EndpointResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId
    ) {
        return endpointService.list(tenantId);
    }

    @GetMapping("/{id}")
    public EndpointResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id
    ) {
        return endpointService.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public EndpointResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEndpointRequest request
    ) {
        return endpointService.update(tenantId, id, request);
    }

    @PatchMapping("/{id}/disable")
    public EndpointResponse disable(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id
    ) {
        return endpointService.disable(tenantId, id);
    }

    @PatchMapping("/{id}/enable")
    public EndpointResponse enable(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id
    ) {
        return endpointService.enable(tenantId, id);
    }
}
