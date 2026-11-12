package com.hookforge.common.api;

import com.hookforge.delivery.application.DeliveryNotFoundException;
import com.hookforge.delivery.application.DeliveryReplayNotAllowedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.hookforge.event.application.IdempotencyConflictException;
import java.time.Instant;
import com.hookforge.tenant.application.BootstrapService.BootstrapDisabledException;
import com.hookforge.tenant.application.BootstrapService.BootstrapAlreadyCompletedException;
import com.hookforge.tenant.application.BootstrapService.InvalidBootstrapSecretException;


@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);
            
@ExceptionHandler(InvalidBootstrapSecretException.class)
public ResponseEntity<ApiError> handleInvalidBootstrapSecret(
        InvalidBootstrapSecretException exception,
        HttpServletRequest request
) {
    return error(
            HttpStatus.UNAUTHORIZED,
            "Invalid bootstrap secret",
            request.getRequestURI()
    );
}

@ExceptionHandler(BootstrapAlreadyCompletedException.class)
public ResponseEntity<ApiError> handleBootstrapAlreadyCompleted(
        BootstrapAlreadyCompletedException exception,
        HttpServletRequest request
) {
    return error(
            HttpStatus.CONFLICT,
            "Bootstrap has already been completed",
            request.getRequestURI()
    );
}

@ExceptionHandler(BootstrapDisabledException.class)
public ResponseEntity<ApiError> handleBootstrapDisabled(
        BootstrapDisabledException exception,
        HttpServletRequest request
) {
    return error(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Bootstrap is disabled or unavailable",
            request.getRequestURI()
    );
}
@ExceptionHandler(IdempotencyConflictException.class)
public ResponseEntity<ApiError> handleIdempotencyConflict(
        IdempotencyConflictException exception,
        HttpServletRequest request
) {
    return error(
            HttpStatus.CONFLICT,
            exception.getMessage(),
            request.getRequestURI()
    );
}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": "
                        + error.getDefaultMessage())
                .orElse("Request validation failed");

        return error(
                HttpStatus.BAD_REQUEST,
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Malformed or invalid request body",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled API error on {}",
                request.getRequestURI(),
                exception
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String message,
            String path
    ) {
        return ResponseEntity.status(status).body(
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        path
                )
        );
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }
    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<ApiError> handleDeliveryNotFound(
            DeliveryNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "Delivery not found",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(DeliveryReplayNotAllowedException.class)
    public ResponseEntity<ApiError> handleDeliveryReplayNotAllowed(
            DeliveryReplayNotAllowedException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONFLICT,
                "Delivery cannot be replayed in its current state",
                request.getRequestURI()
        );
    }
}