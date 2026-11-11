
package com.hookforge.common.api;

import com.hookforge.delivery.application.DeliveryNotFoundException;
import com.hookforge.delivery.application.DeliveryReplayNotAllowedException;
import com.hookforge.event.application.IdempotencyConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler =
            new ApiExceptionHandler();

    @Test
    void deliveryNotFoundShouldReturn404WithoutInternalId()
    {
        UUID deliveryId = UUID.randomUUID();

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/deliveries/" + deliveryId);

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.handleDeliveryNotFound(
                        new DeliveryNotFoundException(deliveryId),
                        request
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Delivery not found", response.getBody().message());
        assertFalse(response.getBody().message().contains(deliveryId.toString()));
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void replayNotAllowedShouldReturn409WithoutInternalDetails()
    {
        UUID deliveryId = UUID.randomUUID();

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/deliveries/" + deliveryId + "/replay");

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.handleDeliveryReplayNotAllowed(
                        new DeliveryReplayNotAllowedException(
                                deliveryId, "SUCCEEDED"
                        ),
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Delivery cannot be replayed in its current state",
                response.getBody().message()
        );
        assertFalse(response.getBody().message().contains(deliveryId.toString()));
        assertFalse(response.getBody().message().contains("SUCCEEDED"));
    }

    @Test
    void idempotencyConflictShouldReturn409()
    {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/events");

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.handleIdempotencyConflict(
                        new IdempotencyConflictException(
                                "Idempotency key conflict"
                        ),
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
    }
}
