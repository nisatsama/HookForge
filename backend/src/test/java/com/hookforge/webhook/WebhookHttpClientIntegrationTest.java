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
import java.net.ServerSocket;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.TimeValue;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private long responseDelayMs = 0;

    private boolean dropResponse = false;

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
                            StandardCharsets.UTF_8
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

            // Simulate a slow destination before it sends response headers.
            if (responseDelayMs > 0) {
                try {
                    Thread.sleep(responseDelayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
            }

            // Simulate a receiver that gets the request but loses its response.
            if (dropResponse) {
                exchange.close();
                return;
            }

            byte[] responseBytes = responseBody.getBytes(
                    StandardCharsets.UTF_8
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

        webhookHttpClient = createWebhookHttpClient(
                Duration.ofSeconds(3)
        );

        when(encryptionService.decrypt(ENCRYPTED_SECRET))
                .thenReturn("test-signing-secret");
    }

    private WebhookHttpClient createWebhookHttpClient(Duration timeout) {

        DnsResolver testDnsResolver = new DnsResolver() {

            @Override
            public InetAddress[] resolve(String host)
                    throws UnknownHostException {
                // Test-only resolver: permits the local HTTP test server.
                return InetAddress.getAllByName(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };

        var connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(testDnsResolver)
                        .setConnectionTimeToLive(TimeValue.ofMinutes(1))
                        .build();

        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.of(timeout))
                .setRedirectsEnabled(false)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();

        return new WebhookHttpClient(
                destinationSecurityService,
                new WebHookRequestSigner(),
                encryptionService,
                httpClient,
                timeout
        );
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

    @Test
    void shouldThrowIOExceptionWhenDestinationConnectionIsRefused()
            throws Exception {

        int unusedPort;

        // Reserve an ephemeral port, then close it so no server is listening.
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        String unreachableUrl =
                "http://127.0.0.1:" + unusedPort + "/";

        WebhookEndpoint endpoint = new WebhookEndpoint(
                UUID.randomUUID(),
                "Unavailable Endpoint",
                unreachableUrl,
                "test-secret-hash",
                ENCRYPTED_SECRET
        );

        assertThrows(
                IOException.class,
                () -> webhookHttpClient.send(
                        UUID.randomUUID(),
                        endpoint,
                        REQUEST_BODY
                )
        );
    }

    @Test
    void shouldThrowHttpTimeoutExceptionWhenDestinationRespondsTooSlowly()
            throws Exception {

        responseDelayMs = 1500;

        WebhookHttpClient shortTimeoutClient =
                createWebhookHttpClient(Duration.ofMillis(300));

        assertThrows(
                IOException.class,
                () -> shortTimeoutClient.send(
                        UUID.randomUUID(),
                        createEndpoint(),
                        REQUEST_BODY
                )
        );
    }

    @Test
    void shouldThrowIOExceptionWhenReceiverProcessesRequestButResponseIsLost()
            throws Exception {

        dropResponse = true;

        UUID deliveryId = UUID.randomUUID();

        WebhookEndpoint endpoint = createEndpoint();

        assertThrows(
                IOException.class,
                () -> webhookHttpClient.send(
                        deliveryId,
                        endpoint,
                        REQUEST_BODY
                )
        );

        // The receiver got the complete request.
        assertEquals(REQUEST_BODY, receivedRequestBody.get());

        // The receiver also received the delivery ID.
        assertEquals(
                deliveryId.toString(),
                receivedDeliveryId.get()
        );

        // The request was signed.
        assertNotNull(receivedSignature.get());
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