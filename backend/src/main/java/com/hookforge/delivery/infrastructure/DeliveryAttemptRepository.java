
package com.hookforge.delivery.infrastructure;

import com.hookforge.delivery.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository
        extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findAllByDeliveryIdOrderByStartedAtDesc(
            UUID deliveryId
    );
}
