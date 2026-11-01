
package com.hookforge.delivery.application;

import java.util.UUID;

public class DeliveryReplayNotAllowedException extends RuntimeException {

    public DeliveryReplayNotAllowedException(
            UUID deliveryId,
            String currentStatus
    ) {
        super(
                "Delivery " + deliveryId
                        + " cannot be replayed from status "
                        + currentStatus
        );
    }
}
