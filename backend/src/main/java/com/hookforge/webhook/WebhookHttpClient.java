
package com.hookforge.webhook;

import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.security.WebhookSecretEncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class WebhookHttpClient {

    private static final int MAX_RESPONSE_BYTES = 8 * 1024;

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    private final DestinationSecurityService destinationSecurityService;
    private final WebHookRequestSigner requestSigner;
    private final WebhookSecretEncryptionService encryptionService;

    @Autowired
    public WebhookHttpClient(
            DestinationSecurityService destinationSecurityService,
            WebHookRequestSigner requestSigner,
            WebhookSecretEncryptionService encryptionService
    ) {
        this(
                destinationSecurityService,
                requestSigner,
                encryptionService,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                REQUEST_TIMEOUT
        );
    }

    // Package-private constructor for controlled HTTP testing.
    WebhookHttpClient(
            DestinationSecurityService destinationSecurityService,
            WebHookRequestSigner requestSigner,
            WebhookSecretEncryptionService encryptionService,
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        this.destinationSecurityService = destinationSecurityService;
        this.requestSigner = requestSigner;
        this.encryptionService = encryptionService;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    public DeliveryResponse send(
            UUID deliveryId,
            WebhookEndpoint endpoint,
            String requestBody
    ) throws IOException, InterruptedException {

        if (deliveryId == null) {
            throw new IllegalArgumentException("Delivery ID is required");
        }

        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint is required");
        }

        if (requestBody == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (endpoint.getSecretEncrypted() == null
                || endpoint.getSecretEncrypted().isBlank()) {
            throw new IllegalStateException(
                    "Endpoint has no encrypted signing secret"
            );
        }

        destinationSecurityService.validateDestination(endpoint.getUrl());

        URI uri = URI.create(endpoint.getUrl());

        String secret = encryptionService.decrypt(
                endpoint.getSecretEncrypted()
        );

        long timestamp = Instant.now().getEpochSecond();

        String signature = requestSigner.sign(
                secret,
                timestamp,
                requestBody
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "HookForge/1.0")
                .header("X-Webhook-Delivery-ID", deliveryId.toString())
                .header("X-Webhook-Timestamp", Long.toString(timestamp))
                .header("X-Webhook-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        long startedNanos = System.nanoTime();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        String responseBody;

        try (InputStream bodyStream = response.body()) {

            byte[] bytes = bodyStream.readNBytes(
                    MAX_RESPONSE_BYTES + 1
            );

            boolean truncated = bytes.length > MAX_RESPONSE_BYTES;

            int length = Math.min(
                    bytes.length,
                    MAX_RESPONSE_BYTES
            );

            responseBody = new String(
                    bytes,
                    0,
                    length,
                    StandardCharsets.UTF_8
            );

            if (truncated) {
                responseBody += "\n[response truncated]";
            }
        }

        long durationMs = Duration.ofNanos(
                System.nanoTime() - startedNanos
        ).toMillis();

        return new DeliveryResponse(
                response.statusCode(),
                responseBody,
                durationMs
        );
    }

    public record DeliveryResponse(
            int statusCode,
            String responseBody,
            long durationMs
    ) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
