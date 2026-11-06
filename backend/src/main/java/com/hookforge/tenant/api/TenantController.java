
package com.hookforge.tenant.api;

import com.hookforge.tenant.api.dto.CreateTenantRequest;
import com.hookforge.tenant.api.dto.TenantResponse;
import com.hookforge.tenant.application.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(
            @Valid @RequestBody CreateTenantRequest request
    ) {
        return tenantService.create(request);
    }
}
