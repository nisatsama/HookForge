
package com.hookforge.tenant.application;

import com.hookforge.tenant.api.dto.CreateTenantRequest;
import com.hookforge.tenant.api.dto.TenantResponse;
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
    public TenantResponse create(CreateTenantRequest request) {

        String normalizedName = request.name().trim();
        String normalizedSlug = request.slug()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant name cannot be blank"
            );
        }

        if (!normalizedSlug.matches(
                "[a-z0-9]+(?:-[a-z0-9]+)*"
        )) {
            throw new IllegalArgumentException(
                    "Slug must contain lowercase letters, "
                            + "numbers, and single hyphens"
            );
        }

        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException(
                    "A tenant with this slug already exists"
            );
        }

        Tenant tenant = new Tenant(
                normalizedName,
                normalizedSlug
        );

        Tenant savedTenant = tenantRepository.save(tenant);

        return TenantResponse.from(savedTenant);
    }
}
