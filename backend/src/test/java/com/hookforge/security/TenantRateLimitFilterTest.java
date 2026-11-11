
package com.hookforge.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantRateLimitFilterTest {

    private UUID tenantId;
    private TenantRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        filter = new TenantRateLimitFilter(2, 60_000);
        chain = mock(FilterChain.class);

        AuthenticatedTenant principal =
                new AuthenticatedTenant(tenantId, UUID.randomUUID());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of()
                )
        );
    }

    @Test
    void shouldAllowRequestsWithinLimit() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldRejectRequestExceedingLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            if (i == 2) {
                assertEquals(429, response.getStatus());
                assertNotNull(response.getHeader("Retry-After"));
            }
        }

        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void shouldApplyLimitSeparatelyToEachTenant() throws Exception {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        filter.doFilter(firstRequest, firstResponse, chain);

        UUID anotherTenantId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedTenant(
                                anotherTenantId, UUID.randomUUID()
                        ),
                        null,
                        List.of()
                )
        );

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        filter.doFilter(secondRequest, secondResponse, chain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());

        verify(chain, times(2)).doFilter(any(), any());
    }
}
