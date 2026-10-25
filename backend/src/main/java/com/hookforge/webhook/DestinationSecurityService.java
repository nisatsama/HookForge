
package com.hookforge.webhook;

import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

@Service
public class DestinationSecurityService {

    public void validateDestination(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Destination URL is required"
            );
        }

        final URI uri;

        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Destination URL is invalid",
                    ex
            );
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Webhook destinations must use HTTPS"
            );
        }

        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Destination URL must have a valid hostname and no credentials or fragment"
            );
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local")) {
            throw new IllegalArgumentException(
                    "Local destination hostnames are not allowed"
            );
        }

        final InetAddress[] addresses;

        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException(
                    "Destination hostname could not be resolved",
                    ex
            );
        }

        if (addresses.length == 0) {
            throw new IllegalArgumentException(
                    "Destination hostname has no resolved addresses"
            );
        }

        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException(
                        "Destination resolves to a non-public IP address"
                );
            }
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;

            // Explicitly block common special-use IPv4 ranges.
            if (a == 0
                    || a == 10
                    || a == 127
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168)
                    || (a == 192 && b == 0)
                    || (a == 198 && (b == 18 || b == 19))
                    || (a == 198 && b == 51)
                    || (a == 203 && b == 0)
                    || a >= 224) {
                return false;
            }

            return true;
        }

        if (address instanceof Inet6Address) {
            // IPv6 unique-local addresses fc00::/7.
            if ((bytes[0] & 0xfe) == 0xfc) {
                return false;
            }

            // IPv6 addresses in 2001:db8::/32 are documentation-only.
            if ((bytes[0] & 0xff) == 0x20
                    && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8) {
                return false;
            }

            // Reject IPv4-mapped IPv6 addresses to avoid bypasses.
            boolean ipv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    ipv4Mapped = false;
                    break;
                }
            }

            if (ipv4Mapped && bytes[10] == (byte) 0xff
                    && bytes[11] == (byte) 0xff) {
                return false;
            }

            return true;
        }

        return false;
    }
}
