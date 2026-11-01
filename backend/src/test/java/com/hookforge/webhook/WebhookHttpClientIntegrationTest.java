
package com.hookforge.webhook;

import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.security.WebhookSecretEncryptionService;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookHttpClientIntegrationTest {

    private static final String REQUEST_BODY =
            "{\"event\":\"order.created\"}";

    private static final String ENCRYPTED_SECRET =
            "test-encrypted-secret";

    @Mock
    private DestinationSecurityService destinationSecurityService;

    @Mock
    private WebhookSecretEncryptionService encryptionService;

    private HttpServer server;

    private WebhookHttpClient webhookHttpClient;

    private int responseStatus = 200;

    private String responseBody = "accepted";

    private final AtomicReference<String> receivedRequestBody =
            new AtomicReference<>();

    private final AtomicReference<String> receivedDeliveryId =
            new AtomicReference<>();

    private final AtomicReference<String> receivedSignature =
            new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/", exchange -> {

            receivedRequestBody.set(
                    new String(
                            exchange.getRequestBody().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            );

            receivedDeliveryId.set(
                    exchange.getRequestHeaders()
                            .getFirst("X-Webhook-Delivery-ID")
            );

            receivedSignature.set(
                    exchange.getRequestHeaders()
                            .getFirst("X-Webhook-Signature")
            );

            byte[] responseBytes = responseBody.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
            );

            exchange.sendResponseHeaders(
                    responseStatus,
                    responseBytes.length
            );

            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });

        server.start();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        webhookHttpClient = new WebhookHttpClient(
                destinationSecurityService,
                new WebHookRequestSigner(),
                encryptionService,
                httpClient,
                Duration.ofSeconds(3)
        );

        when(encryptionService.decrypt(ENCRYPTED_SECRET))
                .thenReturn("test-signing-secret");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendWebhookSuccessfully() throws Exception {

        responseStatus = 200;
        responseBody = "event received";

        UUID deliveryId = UUID.randomUUID();

        WebhookHttpClient.DeliveryResponse response =
                webhookHttpClient.send(
                        deliveryId,
                        createEndpoint(),
                        REQUEST_BODY
                );

        assertEquals(200, response.statusCode());
        assertEquals("event received", response.responseBody());
        assertTrue(response.isSuccessful());
        assertTrue(response.durationMs() >= 0);

        assertEquals(REQUEST_BODY, receivedRequestBody.get());
        assertEquals(deliveryId.toString(), receivedDeliveryId.get());

        assertNotNull(receivedSignature.get());
        assertTrue(receivedSignature.get().startsWith("t="));

        verify(destinationSecurityService)
                .validateDestination(createEndpointUrl());
    }

    @Test
    void shouldReturn429ResponseForRetryClassification()
            throws Exception {

        responseStatus = 429;
        responseBody = "rate limit exceeded";

        WebhookHttpClient.DeliveryResponse response =
                webhookHttpClient.send(
                        UUID.randomUUID(),
                        createEndpoint(),
                        REQUEST_BODY
                );

        assertEquals(429, response.statusCode());
        assertEquals("rate limit exceeded", response.responseBody());
        assertFalse(response.isSuccessful());
    }

    @Test
    void shouldReturn500ResponseForRetryClassification()
            throws Exception {

        responseStatus = 500;
        responseBody = "internal server error";

        WebhookHttpClient.DeliveryResponse response =
                webhookHttpClient.send(
                        UUID.randomUUID(),
                        createEndpoint(),
                        REQUEST_BODY
                );

        assertEquals(500, response.statusCode());
        assertEquals("internal server error", response.responseBody());
        assertFalse(response.isSuccessful());
    }

    @Test
    void shouldReturn400ResponseForPermanentFailureClassification()
            throws Exception {

        responseStatus = 400;
        responseBody = "invalid payload";

        WebhookHttpClient.DeliveryResponse response =
                webhookHttpClient.send(
                        UUID.randomUUID(),
                        createEndpoint(),
                        REQUEST_BODY
                );

        assertEquals(400, response.statusCode());
        assertEquals("invalid payload", response.responseBody());
        assertFalse(response.isSuccessful());
    }

    private WebhookEndpoint createEndpoint() {

        return new WebhookEndpoint(
                UUID.randomUUID(),
                "Integration Test Endpoint",
                createEndpointUrl(),
                "test-secret-hash",
                ENCRYPTED_SECRET
        );
    }

    private String createEndpointUrl() {
        return "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/";
    }
}
