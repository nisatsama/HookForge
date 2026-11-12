package com.hookforge.tenant.api;

import com.hookforge.tenant.api.dto.CreateTenantRequest;
import com.hookforge.tenant.application.BootstrapService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/bootstrap")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping
    public ResponseEntity<BootstrapService.BootstrapResult> bootstrap(
            @RequestHeader(
                    name = "X-HookForge-Bootstrap-Secret",
                    required = false
            ) String secret,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        BootstrapService.BootstrapResult result =
                bootstrapService.bootstrap(secret, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
}