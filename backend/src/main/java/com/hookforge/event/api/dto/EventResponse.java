package com.hookforge.event.api.dto;

//import com.fasterxml.jackson.databind.JsonNode;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventType,
        JsonNode payload,
        Instant createdAt,
        boolean created
) {
}