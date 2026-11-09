package com.hookforge.identity.application;

import com.hookforge.identity.domain.ApiKey;
import com.hookforge.identity.infrastructure.ApiKeyRepository;
import com.hookforge.tenant.domain.Tenant;
import com.hookforge.tenant.infrastructure.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.hookforge.endpoint.api.EndpointController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import com.hookforge.webhook.WebhookHttpClient;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "hookforge.outbox.poll-delay-ms=3600000",
        "hookforge.outbox.initial-delay-ms=3600000",
        "hookforge.delivery.poll-interval-ms=3600000",
        "hookforge.delivery.recovery-interval-ms=3600000"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class ApiKeyAuthenticationIntegrationTest {

    @Autowired ApiKeyService apiKeyService;
    @Autowired ApiKeyRepository apiKeyRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean WebhookHttpClient webhookHttpClient;
    @MockitoSpyBean EndpointController endpointController;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        tenant = tenantRepository.saveAndFlush(new Tenant("Integration tenant", "it-" + unique));
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsSerializesAndAuthenticatesReturnedSecretThroughSecurityFilterChain() throws Exception {
        var created = apiKeyService.create(tenant.getId(), "integration", null);
        String serialized = objectMapper.writeValueAsString(created);
        var response = objectMapper.readValue(serialized, ApiKeyService.CreatedApiKey.class);

        assertEquals(created.secretKey(), response.secretKey());
        assertEquals(64, apiKeyRepository.findAll().getFirst().getKeyHash().length());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM hookforge_api_key_test.api_keys WHERE id = ?",
                Long.class,
                created.id()));
        assertEquals(tenant.getId(), apiKeyService.authenticate(response.secretKey()).orElseThrow().tenantId());
        mockMvc.perform(get("/api/v1/endpoints")
                        .header("Authorization", "Bearer " + response.secretKey()))
                .andExpect(status().isOk());
        verify(endpointController).list(argThat(principal ->
                principal != null && tenant.getId().equals(principal.tenantId())));
    }

    @Test
    void unauthenticatedRequestReproducesEmptyForbiddenResponseBeforeController() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        verify(endpointController, never()).list(any());
    }

    @Test
    void authenticatedPrincipalWithoutTenantTypeFailsInsideController() throws Exception {
        var nonTenantAuthentication = new UsernamePasswordAuthenticationToken(
                "authenticated-non-tenant-principal", null, List.of());

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(authentication(nonTenantAuthentication)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void rejectsIncorrectKey() {
        var created = apiKeyService.create(tenant.getId(), "integration", null);
        assertTrue(apiKeyService.authenticate(created.secretKey() + "x").isEmpty());
    }

    @Test
    void rejectsRevokedKey() {
        var created = apiKeyService.create(tenant.getId(), "integration", null);
        apiKeyService.revoke(tenant.getId(), created.id());
        assertTrue(apiKeyService.authenticate(created.secretKey()).isEmpty());
    }

    @Test
    void rejectsExpiredKey() {
        String expiredRawKey = "hf_live_test-expired-" + UUID.randomUUID();
        ApiKey expired = new ApiKey(tenant, "expired", "hf_live_testpref", sha256(expiredRawKey), Instant.now().minusSeconds(1));
        apiKeyRepository.saveAndFlush(expired);
        assertTrue(apiKeyService.authenticate(expiredRawKey).isEmpty());
    }

    @Test
    void rejectsKeyForInactiveTenant() {
        var created = apiKeyService.create(tenant.getId(), "integration", null);
        tenant.suspend();
        tenantRepository.saveAndFlush(tenant);
        assertTrue(apiKeyService.authenticate(created.secretKey()).isEmpty());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
