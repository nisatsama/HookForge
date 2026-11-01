
package com.hookforge.delivery.api;

import com.hookforge.delivery.api.dto.DeliveryAttemptResponse;
import com.hookforge.delivery.api.dto.DeliveryResponse;
import com.hookforge.delivery.application.DeliveryNotFoundException;
import com.hookforge.delivery.application.DeliveryQueryService;
import com.hookforge.delivery.application.DeliveryReplayNotAllowedException;
import com.hookforge.delivery.application.DeliveryReplayService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryReplayService deliveryReplayService;

    public DeliveryController(
            DeliveryQueryService deliveryQueryService,
            DeliveryReplayService deliveryReplayService
    ) {
        this.deliveryQueryService = deliveryQueryService;
        this.deliveryReplayService = deliveryReplayService;
    }

    @GetMapping("/{deliveryId}")
    public DeliveryResponse getDelivery(
            @PathVariable UUID deliveryId,
            HttpServletRequest request
    ) {
        UUID tenantId = authenticatedTenantId(request);
        return deliveryQueryService.getDelivery(tenantId, deliveryId);
    }

    @GetMapping("/{deliveryId}/attempts")
    public List<DeliveryAttemptResponse> getAttempts(
            @PathVariable UUID deliveryId,
            HttpServletRequest request
    ) {
        UUID tenantId = authenticatedTenantId(request);
        return deliveryQueryService.getAttempts(tenantId, deliveryId);
    }

    @PostMapping("/{deliveryId}/replay")
    public DeliveryResponse replayDelivery(
            @PathVariable UUID deliveryId,
            HttpServletRequest request
    ) {
        UUID tenantId = authenticatedTenantId(request);
        return deliveryReplayService.replay(tenantId, deliveryId);
    }

    private UUID authenticatedTenantId(HttpServletRequest request) {
        Object value = request.getAttribute("tenantId");

        if (value instanceof UUID tenantId) {
            return tenantId;
        }

        throw new TenantAuthenticationException();
    }

    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            DeliveryNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Delivery not found"));
    }

    @ExceptionHandler(DeliveryReplayNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleReplayNotAllowed(
            DeliveryReplayNotAllowedException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(TenantAuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleMissingTenant() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }

    private static class TenantAuthenticationException
            extends RuntimeException {
    }
}
