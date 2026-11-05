package com.hookforge.delivery.api;

import com.hookforge.delivery.application.DeliveryQueryService;
import com.hookforge.delivery.application.DeliveryReplayService;
import com.hookforge.identity.application.ApiKeyService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
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

        mockMvc.perform(post("/api/v1/deliveries/{deliveryId}/replay", deliveryId)
                        .requestAttr("tenantId", tenantId))
                .andExpect(status().isOk());

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