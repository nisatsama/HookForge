package com.hookforge.event.application;

import com.hookforge.delivery.application.DeliveryService;
import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.endpoint.infrastructure.EndpointRepository;
import com.hookforge.event.domain.WebhookEvent;
import com.hookforge.event.infrastructure.WebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventRoutingService {

    private final WebhookEventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final DeliveryService deliveryService;

    public EventRoutingService(
            WebhookEventRepository eventRepository,
            EndpointRepository endpointRepository,
            DeliveryService deliveryService
    ) {
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public int routeEvent(UUID eventId) {
        WebhookEvent event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Webhook event not found: " + eventId
                        )
                );

        int createdCount = 0;

        for (WebhookEndpoint endpoint :
                endpointRepository.findAllByTenantIdOrderByCreatedAtDesc(
                        event.getTenantId()
                )) {

            if (endpoint.getStatus() != WebhookEndpoint.Status.ACTIVE) {
                continue;
            }

            boolean created = deliveryService.createIfAbsent(
                    event.getId(),
                    endpoint.getId(),
                    event.getTenantId()
            );

            if (created) {
                createdCount++;
            }
        }

        return createdCount;
    }
}