package com.hookforge.messaging;
import tools.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookforge.event.application.EventRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

@Component
public class DeliveryEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(DeliveryEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final EventRoutingService eventRoutingService;

    public DeliveryEventConsumer(
            ObjectMapper objectMapper,
            EventRoutingService eventRoutingService
    ) {
        this.objectMapper = objectMapper;
        this.eventRoutingService = eventRoutingService;
    }

    @KafkaListener(
            topics = "${hookforge.kafka.outbox-topic:hookforge.outbox-events}"
    )
    public void consume(String message) {
        final UUID eventId;

        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode eventIdNode = root.get("eventId");

            if (eventIdNode == null || !eventIdNode.isTextual()) {
                throw new IllegalArgumentException(
                        "Kafka message is missing a valid eventId"
                );
            }

            eventId = UUID.fromString(eventIdNode.asText());

        } catch (IllegalArgumentException ex) {
            log.error("Invalid webhook event message received from Kafka", ex);
            throw new IllegalArgumentException(
                    "Invalid webhook event message",
                    ex
            );
        }

        int created = eventRoutingService.routeEvent(eventId);

        log.info(
                "Processed event {}. Created {} new deliveries.",
                eventId,
                created
        );
    }
}