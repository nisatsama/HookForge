package com.hookforge.demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DemoRateLimiterTest {
    @Test void limitsRequestsAndBoundsTrackedClients() {
        DemoRateLimiter limiter=new DemoRateLimiter();
        for(int i=0;i<120;i++) assertTrue(limiter.allow("192.0.2.1"));
        assertFalse(limiter.allow("192.0.2.1"));
        for(int i=0;i<2047;i++) assertTrue(limiter.allow("client-"+i));
        assertFalse(limiter.allow("new-client-after-cap"));
    }
}
