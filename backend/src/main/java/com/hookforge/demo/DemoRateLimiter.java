package com.hookforge.demo;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DemoRateLimiter {
    private static final int MAX_CLIENTS = 2048;
    private static final int LIMIT = 120;
    private static final long WINDOW_MILLIS = 60_000;
    private final Map<String, Window> windows = new LinkedHashMap<>();

    public synchronized boolean allow(String client) {
        long now = Instant.now().toEpochMilli();
        String key = client == null || client.isBlank() ? "unknown" : client;
        Window window = windows.get(key);
        if (window == null) {
            removeExpired(now);
            if (windows.size() >= MAX_CLIENTS) return false;
            window = new Window(now);
            windows.put(key, window);
        } else if (now - window.startedAt >= WINDOW_MILLIS) {
            window = new Window(now);
            windows.put(key, window);
        }
        return ++window.requests <= LIMIT;
    }

    private void removeExpired(long now) {
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().startedAt >= WINDOW_MILLIS) iterator.remove();
        }
    }

    private static final class Window {
        private final long startedAt;
        private int requests;
        private Window(long startedAt) { this.startedAt = startedAt; }
    }
}
