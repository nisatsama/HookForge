package com.hookforge.delivery.api;

import com.hookforge.delivery.api.dto.DeliveryMetricsSummaryResponse;
import com.hookforge.delivery.application.DeliveryMetricsService;
import com.hookforge.security.AuthenticatedTenant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final DeliveryMetricsService metricsService;

    public MetricsController(DeliveryMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/summary")
    public DeliveryMetricsSummaryResponse getSummary(
            @AuthenticationPrincipal AuthenticatedTenant principal
    ) {
        if (principal == null || principal.tenantId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        return metricsService.getSummary(principal.tenantId());
    }
}