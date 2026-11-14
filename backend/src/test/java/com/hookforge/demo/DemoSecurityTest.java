package com.hookforge.demo;

import com.hookforge.delivery.api.dto.DeliveryMetricsSummaryResponse;
import com.hookforge.delivery.application.DeliveryMetricsService;
import com.hookforge.delivery.application.DeliveryQueryService;
import com.hookforge.endpoint.application.EndpointService;
import com.hookforge.event.application.EventService;
import com.hookforge.identity.application.ApiKeyService;
import com.hookforge.security.ApiKeyAuthenticationFilter;
import com.hookforge.security.SecurityConfig;
import com.hookforge.security.TenantRateLimitFilter;
import com.hookforge.tenant.infrastructure.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import({SecurityConfig.class, ApiKeyAuthenticationFilter.class, TenantRateLimitFilter.class,
        DemoWebConfiguration.class, DemoRequestInterceptor.class})
@AutoConfigureMockMvc
@TestPropertySource(properties="hookforge.demo.tenant-id=a0b00c00-1111-4111-8111-000000000001")
class DemoSecurityTest {
    static final UUID DEMO_ID=UUID.fromString("a0b00c00-1111-4111-8111-000000000001");

    @Autowired MockMvc mvc;
    @MockitoBean DeliveryMetricsService metrics;
    @MockitoBean EndpointService endpoints;
    @MockitoBean EventService events;
    @MockitoBean DeliveryQueryService deliveries;
    @MockitoBean DemoRateLimiter limiter;
    @MockitoBean TenantRepository tenants;
    @MockitoBean ApiKeyService keys;

    @Test void anonymousMayReadDemoButNotRealTenantOrMutateDemo() throws Exception {
        when(limiter.allow(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(tenants.existsByIdAndDemoTrue(DEMO_ID)).thenReturn(true);
        when(metrics.getSummary(DEMO_ID)).thenReturn(new DeliveryMetricsSummaryResponse(1,0,0,0,1,0,0));

        mvc.perform(get("/api/v1/demo/metrics/summary").param("tenantId",UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        verify(metrics).getSummary(DEMO_ID);
        mvc.perform(get("/api/v1/endpoints")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/demo/metrics/summary")).andExpect(status().is4xxClientError());
        when(limiter.allow(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        mvc.perform(get("/api/v1/demo/metrics/summary"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After","60"));
    }
}
