
package com.hookforge.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPollingScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPollingScheduler.class);

    private final OutboxPublisher outboxPublisher;

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(
            fixedDelayString = "${hookforge.outbox.poll-delay-ms:1000}",
            initialDelayString = "${hookforge.outbox.initial-delay-ms:1000}"
    )
    public void poll() {
        int published = outboxPublisher.publishPendingEvents();

        if (published > 0) {
            log.info("Published {} outbox records to Kafka", published);
        }
    }
}
