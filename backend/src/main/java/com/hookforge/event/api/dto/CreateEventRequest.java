package com.hookforge.event.api.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank
        @Size(max = 150)
        String eventType,

        @NotNull
        JsonNode payload
) {
}