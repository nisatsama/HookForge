package com.hookforge.identity.application;

import com.hookforge.identity.api.dto.ApiKeyResponse;
import com.hookforge.identity.domain.ApiKey;

public final class ApiKeyMapper {

    private ApiKeyMapper() {
    }

    public static ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getRevokedAt(),
                apiKey.getCreatedAt()
        );
    }
}