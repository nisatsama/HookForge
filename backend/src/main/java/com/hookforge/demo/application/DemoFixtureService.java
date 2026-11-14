
package com.hookforge.demo.application;

import com.hookforge.demo.api.dto.DemoResponses.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

@Service
public class DemoFixtureService {

    private static final String ENDPOINT_1 =
            "10000000-0000-0000-0000-000000000001";
    private static final String ENDPOINT_2 =
            "10000000-0000-0000-0000-000000000002";
    private static final String ENDPOINT_3 =
            "10000000-0000-0000-0000-000000000003";

    private static final String EVENT_1 =
            "20000000-0000-0000-0000-000000000001";
    private static final String EVENT_2 =
            "20000000-0000-0000-0000-000000000002";
    private static final String EVENT_3 =
            "20000000-0000-0000-0000-000000000003";
    private static final String EVENT_4 =
            "20000000-0000-0000-0000-000000000004";

    private static final String DELIVERY_1 =
            "30000000-0000-0000-0000-000000000001";
    private static final String DELIVERY_2 =
            "30000000-0000-0000-0000-000000000002";
    private static final String DELIVERY_3 =
            "30000000-0000-0000-0000-000000000003";
    private static final String DELIVERY_4 =
            "30000000-0000-0000-0000-000000000004";
    private static final String DELIVERY_5 =
            "30000000-0000-0000-0000-000000000005";

    private static final Instant NOW =
            Instant.parse("2026-11-14T12:00:00Z");

    private final List<EndpointResponse> endpoints = List.of(
            new EndpointResponse(
                    ENDPOINT_1,
                    "Order Service",
                    "https://example.com/webhooks/orders",
                    "ACTIVE",
                    NOW.minusSeconds(86400 * 30)
            ),
            new EndpointResponse(
                    ENDPOINT_2,
                    "Analytics Service",
                    "https://example.com/webhooks/analytics",
                    "ACTIVE",
                    NOW.minusSeconds(86400 * 20)
            ),
            new EndpointResponse(
                    ENDPOINT_3,
                    "Legacy CRM",
                    "https://example.com/webhooks/crm",
                    "DISABLED",
                    NOW.minusSeconds(86400 * 10)
            )
    );

    private final List<EventResponse> events = List.of(
            new EventResponse(
                    EVENT_1,
                    "order.created",
                    Map.of(
                            "orderId", "ORD-2026-1042",
                            "customerId", "CUS-204",
                            "amount", 2499.00,
                            "currency", "INR",
                            "items", List.of(
                                    Map.of(
                                            "productId", "PROD-101",
                                            "quantity", 2
                                    )
                            )
                    ),
                    NOW.minusSeconds(300),
                    true
            ),
            new EventResponse(
                    EVENT_2,
                    "payment.completed",
                    Map.of(
                            "paymentId", "PAY-8821",
                            "orderId", "ORD-2026-1041",
                            "amount", 1499.00,
                            "currency", "INR",
                            "paymentMethod", "UPI"
                    ),
                    NOW.minusSeconds(900),
                    true
            ),
            new EventResponse(
                    EVENT_3,
                    "user.updated",
                    Map.of(
                            "userId", "USR-512",
                            "changedFields", List.of("email", "phone"),
                            "source", "account-settings"
                    ),
                    NOW.minusSeconds(1800),
                    true
            ),
            new EventResponse(
                    EVENT_4,
                    "invoice.created",
                    Map.of(
                            "invoiceId", "INV-2026-301",
                            "orderId", "ORD-2026-1038",
                            "amount", 5999.00,
                            "currency", "INR"
                    ),
                    NOW.minusSeconds(3600),
                    true
            )
    );

    private final List<DeliveryResponse> deliveries = List.of(
            new DeliveryResponse(
                    DELIVERY_1, EVENT_1, ENDPOINT_1,
                    "Order Service", "order.created", "SUCCEEDED",
                    1, null, NOW.minusSeconds(290),
                    NOW.minusSeconds(289),
                    NOW.minusSeconds(300), NOW.minusSeconds(289)
            ),
            new DeliveryResponse(
                    DELIVERY_2, EVENT_2, ENDPOINT_2,
                    "Analytics Service", "payment.completed", "SUCCEEDED",
                    1, null, NOW.minusSeconds(890),
                    NOW.minusSeconds(889),
                    NOW.minusSeconds(900), NOW.minusSeconds(889)
            ),
            new DeliveryResponse(
                    DELIVERY_3, EVENT_3, ENDPOINT_1,
                    "Order Service", "user.updated", "RETRYING",
                    3, NOW.plusSeconds(30), NOW.minusSeconds(60),
                    null,
                    NOW.minusSeconds(1800), NOW.minusSeconds(60)
            ),
            new DeliveryResponse(
                    DELIVERY_4, EVENT_4, ENDPOINT_2,
                    "Analytics Service", "invoice.created", "DEAD",
                    5, null, NOW.minusSeconds(120),
                    null,
                    NOW.minusSeconds(3600), NOW.minusSeconds(120)
            ),
            new DeliveryResponse(
                    DELIVERY_5, EVENT_1, ENDPOINT_3,
                    "Legacy CRM", "order.created", "PENDING",
                    0, NOW.plusSeconds(60), null,
                    null,
                    NOW.minusSeconds(100), NOW.minusSeconds(100)
            )
    );

    private final Map<String, List<AttemptResponse>> attempts = Map.of(
            DELIVERY_1,
            List.of(
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000001",
                            1, 200, "{\"received\":true}", null,
                            142L, NOW.minusSeconds(290), NOW.minusSeconds(289)
                    )
            ),
            DELIVERY_2,
            List.of(
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000002",
                            1, 204, "", null,
                            98L, NOW.minusSeconds(890), NOW.minusSeconds(889)
                    )
            ),
            DELIVERY_3,
            List.of(
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000003",
                            1, 503, "{\"error\":\"temporary_unavailable\"}",
                            "HTTP 503 Service Unavailable",
                            310L, NOW.minusSeconds(300), NOW.minusSeconds(299)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000004",
                            2, 503, "{\"error\":\"temporary_unavailable\"}",
                            "HTTP 503 Service Unavailable",
                            287L, NOW.minusSeconds(120), NOW.minusSeconds(119)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000005",
                            3, 503, "{\"error\":\"temporary_unavailable\"}",
                            "HTTP 503 Service Unavailable",
                            301L, NOW.minusSeconds(60), NOW.minusSeconds(59)
                    )
            ),
            DELIVERY_4,
            List.of(
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000006",
                            1, 500, "{\"error\":\"internal_error\"}",
                            "HTTP 500 Internal Server Error",
                            210L, NOW.minusSeconds(500), NOW.minusSeconds(499)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000007",
                            2, 500, "{\"error\":\"internal_error\"}",
                            "HTTP 500 Internal Server Error",
                            215L, NOW.minusSeconds(400), NOW.minusSeconds(399)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000008",
                            3, 500, "{\"error\":\"internal_error\"}",
                            "HTTP 500 Internal Server Error",
                            208L, NOW.minusSeconds(300), NOW.minusSeconds(299)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000009",
                            4, 500, "{\"error\":\"internal_error\"}",
                            "HTTP 500 Internal Server Error",
                            220L, NOW.minusSeconds(200), NOW.minusSeconds(199)
                    ),
                    new AttemptResponse(
                            "40000000-0000-0000-0000-000000000010",
                            5, 500, "{\"error\":\"internal_error\"}",
                            "HTTP 500 Internal Server Error",
                            205L, NOW.minusSeconds(120), NOW.minusSeconds(119)
                    )
            ),
            DELIVERY_5,
            List.of()
    );

    public OverviewResponse getOverview() {
        return new OverviewResponse(
                new MetricsResponse(
                        1284, 18, 6, 32, 1196, 14, 18
                ),
                deliveries.stream().limit(5).toList()
        );
    }

    public List<EndpointResponse> getEndpoints() {
        return endpoints;
    }

    public List<EventResponse> getEvents() {
        return events;
    }

    public EventResponse getEvent(String id) {
        return events.stream()
                .filter(event -> event.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public PageResponse<EventResponse> getEventsPage(int page, int size) {
        return paginate(events, page, size);
    }

    public PageResponse<DeliveryResponse> getDeliveriesPage(
            int page,
            int size,
            String status
    ) {
        List<DeliveryResponse> filtered = deliveries.stream()
                .filter(delivery ->
                        status == null || status.isBlank()
                                || delivery.status().equalsIgnoreCase(status)
                )
                .sorted(Comparator.comparing(
                        DeliveryResponse::createdAt
                ).reversed())
                .toList();

        return paginate(filtered, page, size);
    }

    public DeliveryResponse getDelivery(String id) {
        return deliveries.stream()
                .filter(delivery -> delivery.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<AttemptResponse> getAttempts(String deliveryId) {
        return attempts.getOrDefault(deliveryId, List.of());
    }

    private <T> PageResponse<T> paginate(
            List<T> source,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);

        int fromIndex = Math.min(safePage * safeSize, source.size());
        int toIndex = Math.min(fromIndex + safeSize, source.size());

        int totalPages = (int) Math.ceil(
                (double) source.size() / safeSize
        );

        return new PageResponse<>(
                source.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                source.size(),
                totalPages,
                safePage >= totalPages - 1
        );
    }
}
