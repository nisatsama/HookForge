package com.hookforge.demo;

import com.hookforge.delivery.api.dto.DeliveryAttemptResponse;
import com.hookforge.delivery.api.dto.DeliveryPageResponse;
import com.hookforge.delivery.api.dto.DeliveryResponse;
import com.hookforge.delivery.api.dto.DeliveryMetricsSummaryResponse;
import com.hookforge.delivery.application.DeliveryMetricsService;
import com.hookforge.delivery.application.DeliveryQueryService;
import com.hookforge.delivery.domain.DeliveryStatus;
import com.hookforge.endpoint.api.dto.EndpointResponse;
import com.hookforge.endpoint.application.EndpointService;
import com.hookforge.event.api.dto.EventResponse;
import com.hookforge.event.application.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final UUID tenantId;
    private final DeliveryMetricsService metrics;
    private final EndpointService endpoints;
    private final EventService events;
    private final DeliveryQueryService deliveries;

    public DemoController(@Value("${hookforge.demo.tenant-id:}") String configuredTenant,
                          DeliveryMetricsService metrics, EndpointService endpoints,
                          EventService events, DeliveryQueryService deliveries) {
        this.tenantId = configuredTenant.isBlank() ? null : UUID.fromString(configuredTenant);
        this.metrics=metrics; this.endpoints=endpoints; this.events=events; this.deliveries=deliveries;
    }
    @GetMapping("/metrics/summary") public DeliveryMetricsSummaryResponse metrics() { return metrics.getSummary(tenantId); }
    @GetMapping("/endpoints") public List<EndpointResponse> endpoints() { return endpoints.list(tenantId); }
    @GetMapping("/events") public EventService.EventPageResponse events(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { validatePage(page,size); return events.listEvents(tenantId,page,size); }
    @GetMapping("/events/{id}") public EventResponse event(@PathVariable UUID id) { return events.getEvent(tenantId,id); }
    @GetMapping("/deliveries") public DeliveryPageResponse deliveries(@RequestParam(required=false) DeliveryStatus status,@RequestParam(required=false) UUID eventId,@RequestParam(required=false) UUID endpointId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { validatePage(page,size); return deliveries.getDeliveries(tenantId,status,eventId,endpointId,page,size); }
    @GetMapping("/deliveries/{id}") public DeliveryResponse delivery(@PathVariable UUID id) { return deliveries.getDelivery(tenantId,id); }
    @GetMapping("/deliveries/{id}/attempts") public List<DeliveryAttemptResponse> attempts(@PathVariable UUID id) { return deliveries.getAttempts(tenantId,id); }
    private static void validatePage(int page,int size) { if(page<0||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination"); }
}
