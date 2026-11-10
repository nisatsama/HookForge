
package com.hookforge.webhook;

import com.hookforge.endpoint.domain.WebhookEndpoint;
import com.hookforge.security.WebhookSecretEncryptionService;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.ClassicHttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
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

    private final CloseableHttpClient httpClient;
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
        this.destinationSecurityService = destinationSecurityService;
        this.requestSigner = requestSigner;
        this.encryptionService = encryptionService;
        this.requestTimeout = REQUEST_TIMEOUT;
        this.httpClient = createHttpClient(
                destinationSecurityService,
                requestTimeout
        );
    }

    // Package-private constructor for integration tests.
    WebhookHttpClient(
            DestinationSecurityService destinationSecurityService,
            WebHookRequestSigner requestSigner,
            WebhookSecretEncryptionService encryptionService,
            CloseableHttpClient httpClient,
            Duration requestTimeout
    ) {
        this.destinationSecurityService = destinationSecurityService;
        this.requestSigner = requestSigner;
        this.encryptionService = encryptionService;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    private static CloseableHttpClient createHttpClient(
            DestinationSecurityService securityService,
            Duration requestTimeout
    ) {
        DnsResolver dnsResolver = new DnsResolver() {

            @Override
            public InetAddress[] resolve(String host)
                    throws UnknownHostException {
                return securityService.resolvePublicAddresses(host);
            }

            @Override
            public String resolveCanonicalHostname(String host)
                    throws UnknownHostException {
                return host;
            }
        };

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setConnectionTimeToLive(TimeValue.ofMinutes(1))
                        .setMaxConnTotal(100)
                        .setMaxConnPerRoute(10)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                .setResponseTimeout(Timeout.of(requestTimeout))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setRedirectsEnabled(false)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableContentCompression()
                .build();
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

        // Preflight validation. DNS is also checked again by the
        // connection manager immediately before opening a connection.
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

        HttpPost request = new HttpPost(uri);

        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "HookForge/1.0");
        request.setHeader("X-Webhook-Delivery-ID", deliveryId.toString());
        request.setHeader("X-Webhook-Timestamp", Long.toString(timestamp));
        request.setHeader("X-Webhook-Signature", signature);

        request.setEntity(
                new StringEntity(requestBody, ContentType.APPLICATION_JSON)
        );

        RequestConfig perRequestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                .setResponseTimeout(Timeout.of(requestTimeout))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setRedirectsEnabled(false)
                .build();

        request.setConfig(perRequestConfig);

        long startedNanos = System.nanoTime();

        String responseBody;
        int statusCode;

        try (ClassicHttpResponse response = httpClient.execute(request)) {

            statusCode = response.getCode();

            responseBody = readResponseBody(response.getEntity());
        }

        long durationMs = Duration.ofNanos(
                System.nanoTime() - startedNanos
        ).toMillis();

        return new DeliveryResponse(
                statusCode,
                responseBody,
                durationMs
        );
    }

    private String readResponseBody(
            org.apache.hc.core5.http.HttpEntity entity
    ) throws IOException {

        if (entity == null) {
            return "";
        }

        try (InputStream bodyStream = entity.getContent()) {

            byte[] bytes = bodyStream.readNBytes(
                    MAX_RESPONSE_BYTES + 1
            );

            boolean truncated = bytes.length > MAX_RESPONSE_BYTES;

            int length = Math.min(
                    bytes.length,
                    MAX_RESPONSE_BYTES
            );

            String responseBody = new String(
                    bytes,
                    0,
                    length,
                    java.nio.charset.StandardCharsets.UTF_8
            );

            if (truncated) {
                responseBody += "\n[response truncated]";
            }

            return responseBody;
        }
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
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
