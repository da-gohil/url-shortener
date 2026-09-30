package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Stops the server being used to reach places only it can reach (SSRF).
 *
 * <p>Before the shortener fetches a submitted URL (to check it exists), every host it
 * would connect to - the original and each redirect - must resolve only to public
 * internet addresses. Loopback, private ranges, link-local (which includes the cloud
 * metadata endpoint 169.254.169.254) and the like are refused.
 *
 * <p>Known gap: a host can resolve to a public address here and a private one a moment
 * later when the connection is made (DNS rebinding). Closing that needs connecting to
 * the checked address itself, or an egress proxy; out of scope for this project.
 */
@Component
public class DestinationGuard {

    static final String PRIVATE_ADDRESS =
            "That address points to a private or local network, which can't be shortened.";

    /**
     * @throws UnsafeUrlException if the scheme isn't http(s) or the host resolves to a
     *         non-public address. A host that doesn't resolve at all passes: the caller
     *         reports it as unreachable.
     */
    public void check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UnsafeUrlException("Only http and https links can be shortened.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("That link has no host name.");
        }
        InetAddress[] addresses;
        try {
            addresses = resolve(host);
        } catch (UnknownHostException e) {
            return;
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new UnsafeUrlException(PRIVATE_ADDRESS);
            }
        }
    }

    /** Overridable so tests can resolve names without real DNS. */
    protected InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            return first != 0                                        // 0.0.0.0/8 "this network"
                    && !(first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 carrier NAT
                    && !(first == 192 && second == 0 && (b[2] & 0xFF) == 0) // 192.0.0.0/24
                    && !(first == 198 && (second == 18 || second == 19))    // 198.18.0.0/15 benchmarking
                    && first < 240;                                  // 240.0.0.0/4 reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            if ((b[0] & 0xFE) == 0xFC) {                             // fc00::/7 unique local
                return false;
            }
            if (isIpv4Embedded(b)) {                                 // ::ffff:a.b.c.d and ::a.b.c.d
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isIpv4Embedded(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xFF && b[11] == (byte) 0xFF);
    }
}
