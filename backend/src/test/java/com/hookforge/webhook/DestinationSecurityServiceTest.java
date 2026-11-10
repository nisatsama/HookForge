
package com.hookforge.webhook;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class DestinationSecurityServiceTest {

    private final DestinationSecurityService service =
            new DestinationSecurityService();

    @Test
    void shouldAcceptPublicIpv4Address() throws Exception {
        assertTrue(isPublic("8.8.8.8"));
        assertTrue(isPublic("1.1.1.1"));
    }

    @Test
    void shouldRejectLoopbackAddresses() throws Exception {
        assertFalse(isPublic("127.0.0.1"));
        assertFalse(isPublic("::1"));
    }

    @Test
    void shouldRejectPrivateIpv4Addresses() throws Exception {
        assertFalse(isPublic("10.0.0.1"));
        assertFalse(isPublic("172.16.0.1"));
        assertFalse(isPublic("192.168.1.1"));
    }

    @Test
    void shouldRejectLinkLocalAddresses() throws Exception {
        assertFalse(isPublic("169.254.1.1"));
        assertFalse(isPublic("fe80::1"));
    }

    @Test
    void shouldRejectDocumentationAddresses() throws Exception {
        assertFalse(isPublic("192.0.2.1"));
        assertFalse(isPublic("198.51.100.1"));
        assertFalse(isPublic("203.0.113.1"));
        assertFalse(isPublic("2001:db8::1"));
    }

    @Test
    void shouldRejectIpv6UniqueLocalAddresses() throws Exception {
        assertFalse(isPublic("fc00::1"));
        assertFalse(isPublic("fd00::1"));
    }

    @Test
    void shouldRejectMulticastAddresses() throws Exception {
        assertFalse(isPublic("224.0.0.1"));
        assertFalse(isPublic("ff02::1"));
    }

    @Test
    void shouldRejectMixedPublicAndPrivateDnsResults() throws Exception {
        assertThrows(
                PermanentDestinationException.class,
                () -> service.validateResolvedAddresses(
                        new InetAddress[]{
                                InetAddress.getByAddress(
                                        new byte[]{8, 8, 8, 8}
                                ),
                                InetAddress.getByAddress(
                                        new byte[]{10, 0, 0, 1}
                                )
                        }
                )
        );
    }

    @Test
    void shouldRejectEmptyDnsResults() {
        assertThrows(
                PermanentDestinationException.class,
                () -> service.validateResolvedAddresses(new InetAddress[0])
        );
    }

    private boolean isPublic(String address) throws Exception {
        var method = DestinationSecurityService.class
                .getDeclaredMethod("isPublicAddress", InetAddress.class);

        method.setAccessible(true);

        return (boolean) method.invoke(
                service,
                InetAddress.getByName(address)
        );
    }
}
