
package com.hookforge.delivery.infrastructure;

import com.hookforge.delivery.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAttemptRepository
        extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findAllByDeliveryIdOrderByStartedAtDesc(
            UUID deliveryId
    );

    Optional<DeliveryAttempt> findByDeliveryIdAndCompletedAtIsNull(
            UUID deliveryId
    );

    Optional<DeliveryAttempt> findByDeliveryIdAndAttemptNumber(
            UUID deliveryId,
            int attemptNumber
    );

    @Query("""
            SELECT COALESCE(MAX(a.attemptNumber), 0)
            FROM DeliveryAttempt a
            WHERE a.deliveryId = :deliveryId
            """)
    int findMaxAttemptNumberByDeliveryId(
            @Param("deliveryId") UUID deliveryId
    );
}
