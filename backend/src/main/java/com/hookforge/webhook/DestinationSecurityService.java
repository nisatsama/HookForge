
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
            throw new PermanentDestinationException("Destination URL is required");
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new PermanentDestinationException("Destination URL is invalid", ex);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new PermanentDestinationException(
                    "Webhook destinations must use HTTPS"
            );
        }

        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new PermanentDestinationException(
                    "Destination URL must have a valid hostname and no credentials or fragment"
            );
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")) {
            throw new PermanentDestinationException(
                    "Local destination hostnames are not allowed"
            );
        }

        resolvePublicAddresses(host);
    }
    public InetAddress[] resolvePublicAddresses(String host) {


        final InetAddress[] addresses;

        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new RetryableDestinationException(
                    "Destination hostname could not be resolved",
                    ex
            );
        }

        if (addresses.length == 0) {
            throw new PermanentDestinationException(
                    "Destination hostname has no resolved addresses"
            );
        }

        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new PermanentDestinationException(
                        "Destination resolves to a non-public IP address"
                );
            }
        }

        return addresses;
    }
    private boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            return !matchesPrefix(bytes, new int[]{0, 0, 0, 0}, 8)
                    && !matchesPrefix(bytes, new int[]{10, 0, 0, 0}, 8)
                    && !matchesPrefix(bytes, new int[]{100, 64, 0, 0}, 10)
                    && !matchesPrefix(bytes, new int[]{127, 0, 0, 0}, 8)
                    && !matchesPrefix(bytes, new int[]{169, 254, 0, 0}, 16)
                    && !matchesPrefix(bytes, new int[]{172, 16, 0, 0}, 12)
                    && !matchesPrefix(bytes, new int[]{192, 0, 0, 0}, 24)
                    && !matchesPrefix(bytes, new int[]{192, 0, 2, 0}, 24)
                    && !matchesPrefix(bytes, new int[]{192, 88, 99, 0}, 24)
                    && !matchesPrefix(bytes, new int[]{192, 168, 0, 0}, 16)
                    && !matchesPrefix(bytes, new int[]{198, 18, 0, 0}, 15)
                    && !matchesPrefix(bytes, new int[]{198, 51, 100, 0}, 24)
                    && !matchesPrefix(bytes, new int[]{203, 0, 113, 0}, 24)
                    && !matchesPrefix(bytes, new int[]{224, 0, 0, 0}, 4)
                    && !matchesPrefix(bytes, new int[]{240, 0, 0, 0}, 4);
        }

        if (address instanceof Inet6Address) {
            // Allow only IPv6 global-unicast space: 2000::/3.
            if (!matchesPrefix(bytes, new int[]{
                    0x20, 0x00, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
            }, 3)) {
                return false;
            }

            // Reject special-purpose IPv6 ranges conservatively.
            if (matchesPrefix(bytes, new int[]{
                    0x20, 0x01, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
            }, 23)
                    || matchesPrefix(bytes, new int[]{
                    0x20, 0x01, 0x00, 0x20, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
            }, 28)
                    || matchesPrefix(bytes, new int[]{
                    0x20, 0x02, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
            }, 16)
                    || matchesPrefix(bytes, new int[]{
                    0x3f, 0xff, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
            }, 20)) {
                return false;
            }

            // Reject IPv4-mapped IPv6 addresses.
            boolean ipv4Mapped = true;

            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    ipv4Mapped = false;
                    break;
                }
            }

            if (ipv4Mapped
                    && bytes[10] == (byte) 0xff
                    && bytes[11] == (byte) 0xff) {
                return false;
            }

            return true;
        }

        return false;
    }

    private boolean matchesPrefix(
            byte[] address,
            int[] network,
            int prefixLength
    ) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if ((address[i] & 0xff) != network[i]) {
                return false;
            }
        }

        if (remainingBits > 0) {
            int mask = 0xff << (8 - remainingBits);

            return ((address[fullBytes] & 0xff) & mask)
                    == (network[fullBytes] & mask);
        }

        return true;
    }
}
