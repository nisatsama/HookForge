package com.hookforge.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {
    }

    public static UUID getTenantId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof AuthenticatedTenant tenant)) {
            throw new IllegalStateException(
                    "No authenticated tenant is available"
            );
        }

        return tenant.tenantId();
    }
}