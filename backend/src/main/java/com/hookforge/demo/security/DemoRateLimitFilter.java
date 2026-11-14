
package com.hookforge.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DemoRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int requestLimit;
    private final long windowMillis;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public DemoRateLimitFilter(
            @Value("${hookforge.demo.rate-limit.requests:60}")
            int requestLimit,
            @Value("${hookforge.demo.rate-limit.window-ms:60000}")
            long windowMillis
    ) {
        if (requestLimit < 1 || windowMillis < 1000) {
            throw new IllegalArgumentException(
                    "Invalid demo rate limit configuration"
            );
        }

        this.requestLimit = requestLimit;
        this.windowMillis = windowMillis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/demo/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        long now = Instant.now().toEpochMilli();

        Window existing = windows.get(clientIp);

        if (existing == null && windows.size() >= MAX_TRACKED_CLIENTS) {
            cleanupExpiredWindows(now);

            if (windows.size() >= MAX_TRACKED_CLIENTS) {
                response.setHeader("Retry-After", "60");
                response.sendError(
                        429,
                        "Demo service is temporarily rate limited"
                );
                return;
            }
        }

        Window window = windows.compute(clientIp, (ip, current) -> {
            if (current == null || now - current.startedAt >= windowMillis) {
                return new Window(now);
            }
            return current;
        });

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

            response.setHeader(
                    "Retry-After",
                    String.valueOf(retryAfterSeconds)
            );

            response.sendError(
                    429,
                    "Demo request limit exceeded"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void cleanupExpiredWindows(long now) {
        Iterator<Map.Entry<String, Window>> iterator =
                windows.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Window> entry = iterator.next();

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
