
package com.hookforge.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(

        @NotBlank(message = "Tenant name is required")
        String name,

        @NotBlank(message = "Tenant slug is required")
        String slug

) {
}
