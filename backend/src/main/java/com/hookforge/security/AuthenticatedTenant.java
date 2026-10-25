package com.hookforge.security;

import java.util.UUID;

public record AuthenticatedTenant(
        UUID tenantId,
        UUID apiKeyId
) {
}