
package com.hookforge.demo.api;

import com.hookforge.demo.api.dto.DemoResponses.*;
import com.hookforge.demo.application.DemoFixtureService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoFixtureService demoFixtureService;

    public DemoController(DemoFixtureService demoFixtureService) {
        this.demoFixtureService = demoFixtureService;
    }

    @GetMapping("/overview")
    public OverviewResponse getOverview() {
        return demoFixtureService.getOverview();
    }

    @GetMapping("/endpoints")
    public List<EndpointResponse> getEndpoints() {
        return demoFixtureService.getEndpoints();
    }

    @GetMapping("/events")
    public PageResponse<EventResponse> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        validatePagination(page, size);
        return demoFixtureService.getEventsPage(page, size);
    }

    @GetMapping("/events/{id}")
    public EventResponse getEvent(@PathVariable String id) {
        EventResponse event = demoFixtureService.getEvent(id);

        if (event == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Demo event not found"
            );
        }

        return event;
    }

    @GetMapping("/deliveries")
    public PageResponse<DeliveryResponse> getDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status
    ) {
        validatePagination(page, size);
        return demoFixtureService.getDeliveriesPage(page, size, status);
    }

    @GetMapping("/deliveries/{id}")
    public DeliveryResponse getDelivery(@PathVariable String id) {
        DeliveryResponse delivery = demoFixtureService.getDelivery(id);

        if (delivery == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Demo delivery not found"
            );
        }

        return delivery;
    }

    @GetMapping("/deliveries/{id}/attempts")
    public List<AttemptResponse> getAttempts(@PathVariable String id) {
        if (demoFixtureService.getDelivery(id) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Demo delivery not found"
            );
        }

        return demoFixtureService.getAttempts(id);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 50"
            );
        }
    }
}
