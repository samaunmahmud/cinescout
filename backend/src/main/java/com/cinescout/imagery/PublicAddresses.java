package com.cinescout.imagery;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Whether a host is out on the public internet. A venue's web address can be typed in by a user, and the server
 * then fetches it: without this check, "http://169.254.169.254/" or "http://localhost:8081/actuator" would make
 * the server read its own surroundings on the user's behalf.
 */
public final class PublicAddresses {

    private PublicAddresses() {
    }

    /** False for anything that resolves (even in part) to a loopback, private, link-local or otherwise local address. Blocking: it resolves the name. */
    public static boolean isPublic(String host) {
        if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost") || host.toLowerCase().endsWith(".localhost")
                || host.toLowerCase().endsWith(".local") || host.toLowerCase().endsWith(".internal")) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException | SecurityException e) {
            return false;
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            // Unique local addresses (fc00::/7), the IPv6 private range.
            return (bytes[0] & 0xfe) != 0xfc;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        // Carrier-grade NAT (100.64.0.0/10) and "this network" (0.0.0.0/8) are not the public internet either.
        return !(first == 0 || (first == 100 && second >= 64 && second < 128));
    }
}
