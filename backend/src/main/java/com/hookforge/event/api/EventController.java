package com.hookforge.event.api;

import com.hookforge.event.api.dto.CreateEventRequest;
import com.hookforge.event.api.dto.EventResponse;
import com.hookforge.event.application.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.hookforge.security.AuthenticatedTenant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedTenant principal
    ) {
        requireTenant(principal);
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @GetMapping
    public EventService.EventPageResponse listEvents(
            @AuthenticationPrincipal AuthenticatedTenant principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = requireTenant(principal);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        return eventService.listEvents(tenantId, page, size);
    }
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedTenant principal
    ) {
        requireTenant(principal);
        EventResponse response = eventService.createEvent(
                request,
                idempotencyKey
        );

        HttpStatus status = response.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    private UUID requireTenant(AuthenticatedTenant principal) {
        if (principal == null || principal.tenantId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated tenant is required");
        }
        return principal.tenantId();
    }
}
