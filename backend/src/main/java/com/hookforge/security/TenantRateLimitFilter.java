
package com.hookforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TenantRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_TENANTS = 10_000;

    private final int requestLimit;
    private final long windowMillis;

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    public TenantRateLimitFilter(
            @Value("${hookforge.rate-limit.requests:100}") int requestLimit,
            @Value("${hookforge.rate-limit.window-ms:60000}") long windowMillis
    ) {
        if (requestLimit < 1 || windowMillis < 1000) {
            throw new IllegalArgumentException("Invalid rate limit configuration");
        }

        this.requestLimit = requestLimit;
        this.windowMillis = windowMillis;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedTenant tenant)) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().toEpochMilli();

        Window window = windows.compute(tenant.tenantId(), (id, existing) -> {
            if (existing == null || now - existing.startedAt >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });

        if (windows.size() > MAX_TRACKED_TENANTS) {
            cleanupExpiredWindows(now);
        }

        int count = window.requests.incrementAndGet();

        if (count > requestLimit) {
            long remainingMillis = Math.max(
                    1,
                    window.startedAt + windowMillis - now
            );

            long retryAfterSeconds = Math.max(
                    1,
                    (remainingMillis + 999) / 1000
            );

            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.sendError(
                    HttpServletResponse.SC_TOO_MANY_REQUESTS,
                    "Tenant request limit exceeded"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void cleanupExpiredWindows(long now) {
        Iterator<Map.Entry<UUID, Window>> iterator =
                windows.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Window> entry = iterator.next();

            if (now - entry.getValue().startedAt >= windowMillis) {
                windows.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger requests = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
