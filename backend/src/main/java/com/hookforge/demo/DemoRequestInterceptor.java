package com.hookforge.demo;

import com.hookforge.tenant.infrastructure.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.UUID;

@Component
public class DemoRequestInterceptor implements HandlerInterceptor {
    private final UUID demoTenantId;
    private final DemoRateLimiter limiter;
    private final TenantRepository tenants;

    public DemoRequestInterceptor(@Value("${hookforge.demo.tenant-id:}") String configuredTenant,
                                  DemoRateLimiter limiter, TenantRepository tenants) {
        this.demoTenantId = configuredTenant.isBlank() ? null : UUID.fromString(configuredTenant);
        this.limiter = limiter;
        this.tenants = tenants;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!limiter.allow(request.getRemoteAddr())) {
            writeError(response, 429, "Too Many Requests", "60");
            return false;
        }
        if (demoTenantId == null || !tenants.existsByIdAndDemoTrue(demoTenantId)) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Demo workspace is not configured", null);
            return false;
        }
        return true;
    }

    private static void writeError(HttpServletResponse response, int status, String message, String retryAfter) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
