
package com.hookforge.messaging;

import com.hookforge.outbox.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class DeliveryEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper jackson3Mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;
    private final String topic;

    public DeliveryEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper jackson3Mapper,
            com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper,
            @Value("${hookforge.kafka.outbox-topic:hookforge.outbox-events}")
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jackson3Mapper = jackson3Mapper;
        this.jackson2Mapper = jackson2Mapper;
        this.topic = topic;
    }

    public void publish(OutboxEvent event) {
        try {
            JsonNode payload = jackson3Mapper.readTree(
                    jackson2Mapper.writeValueAsString(event.getPayload())
            );

            OutboxMessage message = new OutboxMessage(
                    event.getId().toString(),
                    event.getEventId().toString(),
                    event.getEventType(),
                    payload,
                    event.getCreatedAt()
            );

            String json = jackson3Mapper.writeValueAsString(message);

            kafkaTemplate.send(
                    topic,
                    event.getEventId().toString(),
                    json
            ).get(10, TimeUnit.SECONDS);

        } catch (
                JacksonException
                | com.fasterxml.jackson.core.JsonProcessingException ex
        ) {
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
        } catch (ExecutionException | TimeoutException ex) {
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
            JsonNode payload,
            Instant createdAt
    ) {
    }
}
