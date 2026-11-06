
package com.hookforge.tenant.application;

import com.hookforge.tenant.domain.Tenant;
import com.hookforge.tenant.infrastructure.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Tenant create(String name, String slug) {

        // Normalize input
        String normalizedName = name.trim();
        String normalizedSlug = slug.trim()
                .toLowerCase(Locale.ROOT);

        // Validate tenant name
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant name cannot be blank"
            );
        }

        // Validate slug format
        if (!normalizedSlug.matches(
                "[a-z0-9]+(?:-[a-z0-9]+)*"
        )) {
            throw new IllegalArgumentException(
                    "Slug must contain only lowercase letters, "
                            + "numbers, and single hyphens"
            );
        }

        // Prevent duplicate slugs
        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException(
                    "A tenant with this slug already exists"
            );
        }

        // Create and persist tenant
        Tenant tenant = new Tenant(
                normalizedName,
                normalizedSlug
        );

        return tenantRepository.save(tenant);
    }
}
