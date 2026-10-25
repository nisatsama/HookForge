package com.hookforge.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateApiKeyRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        Instant expiresAt
) {
}