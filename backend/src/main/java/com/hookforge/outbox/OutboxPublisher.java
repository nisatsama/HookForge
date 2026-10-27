
        package com.hookforge.outbox;

import com.hookforge.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

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
    private final ObjectMapper objectMapper;
    private final String topic;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hookforge.kafka.outbox-topic:hookforge.outbox-events}")
            String topic
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> events =
                outboxRepository.lockPendingBatch(BATCH_SIZE);

        int publishedCount = 0;

        for (OutboxEvent event : events) {
            try {
                String message = objectMapper.writeValueAsString(
                        new OutboxMessage(
                                event.getId().toString(),
                                event.getEventId().toString(),
                                event.getEventType(),
                                event.getPayload(),
                                event.getCreatedAt()
                        )
                );

                // Wait for Kafka acknowledgement before marking published.
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

            } catch (JacksonException
                     | ExecutionException
                     | TimeoutException ex) {

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
