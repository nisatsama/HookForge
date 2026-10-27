
package com.hookforge.outbox;

import com.hookforge.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper jackson3Mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper;
    private final String topic;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper jackson3Mapper,
            com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper,
            @Value("${hookforge.kafka.outbox-topic:hookforge.outbox-events}")
            String topic
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.jackson3Mapper = jackson3Mapper;
        this.jackson2Mapper = jackson2Mapper;
        this.topic = topic;
    }

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> events =
                outboxRepository.lockPendingBatch(BATCH_SIZE);

        int publishedCount = 0;

        for (OutboxEvent event : events) {
            try {
                JsonNode payload = jackson3Mapper.readTree(
                        jackson2Mapper.writeValueAsString(event.getPayload())
                );

                OutboxMessage outboxMessage = new OutboxMessage(
                        event.getId().toString(),
                        event.getEventId().toString(),
                        event.getEventType(),
                        payload,
                        event.getCreatedAt()
                );

                String message =
                        jackson3Mapper.writeValueAsString(outboxMessage);

                kafkaTemplate.send(
                        topic,
                        event.getEventId().toString(),
                        message
                ).get(
                        KAFKA_ACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );

                event.markPublished();
                publishedCount++;

                log.debug(
                        "Published outbox record {} for event {}",
                        event.getId(),
                        event.getEventId()
                );

            } catch (
                    JacksonException
                    | com.fasterxml.jackson.core.JsonProcessingException
                    | ExecutionException
                    | TimeoutException ex
            ) {
                recordFailure(event, ex);

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                recordFailure(event, ex);
                break;

            } catch (RuntimeException ex) {
                recordFailure(event, ex);
            }
        }

        return publishedCount;
    }

    private void recordFailure(OutboxEvent event, Exception ex) {
        event.recordFailedAttempt(MAX_ATTEMPTS);

        log.warn(
                "Could not publish outbox record {}. Attempts: {}. Status: {}",
                event.getId(),
                event.getAttempts(),
                event.getStatus(),
                ex
        );
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
