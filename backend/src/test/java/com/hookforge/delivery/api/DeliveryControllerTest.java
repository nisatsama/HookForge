package com.hookforge.delivery.api;

import com.hookforge.HookforgeApplication;
import com.hookforge.delivery.application.DeliveryQueryService;
import com.hookforge.delivery.application.DeliveryReplayService;
import com.hookforge.identity.application.ApiKeyService;
import com.hookforge.security.AuthenticatedTenant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
@ContextConfiguration(classes = HookforgeApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryQueryService deliveryQueryService;

    @MockitoBean
    private DeliveryReplayService deliveryReplayService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @Test
    void replayDeliveryShouldReturnOkForAuthenticatedTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedTenant(tenantId, UUID.randomUUID()), null,
                java.util.List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(post("/api/v1/deliveries/{deliveryId}/replay", deliveryId))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(deliveryReplayService).replay(tenantId, deliveryId);
    }

    @Test
    void replayDeliveryShouldReturnUnauthorizedWhenTenantIsMissing()
            throws Exception {

        UUID deliveryId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/deliveries/{deliveryId}/replay", deliveryId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deliveryReplayService);
    }
}
