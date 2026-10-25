package com.hookforge.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookforge.outbox.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DeliveryEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public DeliveryEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hookforge.kafka.outbox-topic:hookforge.outbox-events}")
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(OutboxEvent event) {
        OutboxMessage message = new OutboxMessage(
                event.getId().toString(),
                event.getEventId().toString(),
                event.getEventType(),
                event.getPayload(),
                event.getCreatedAt()
        );

        try {
            String json = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(
                    topic,
                    event.getEventId().toString(),
                    json
            ).get(10, TimeUnit.SECONDS);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not serialize outbox event " + event.getId(),
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing outbox event " + event.getId(),
                    ex
            );
        } catch (java.util.concurrent.ExecutionException
                 | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException(
                    "Could not publish outbox event " + event.getId(),
                    ex
            );
        }
    }

    private record OutboxMessage(
            String outboxId,
            String eventId,
            String eventType,
            com.fasterxml.jackson.databind.JsonNode payload,
            java.time.Instant createdAt
    ) {
    }
}