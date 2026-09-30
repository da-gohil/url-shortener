package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.8.8.8", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "100.127.255.255", "192.0.0.8",
            "198.18.0.1", "224.0.0.1", "255.255.255.255",
            "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254"})
    void privateLocalAndReservedAddressesAreNotPublic(String address) throws Exception {
        assertThat(DestinationGuard.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "93.184.215.14", "172.32.0.1", "100.128.0.1",
            "2606:4700:4700::1111", "::ffff:8.8.8.8"})
    void ordinaryInternetAddressesArePublic(String address) throws Exception {
        assertThat(DestinationGuard.isPublic(InetAddress.getByName(address))).isTrue();
    }

    /** Resolves a few made-up names without touching real DNS. */
    private final DestinationGuard guard = new DestinationGuard() {
        private final Map<String, String[]> dns = Map.of(
                "public.test", new String[]{"93.184.215.14"},
                "internal.test", new String[]{"10.0.0.7"},
                "sneaky.test", new String[]{"93.184.215.14", "127.0.0.1"});

        @Override
        protected InetAddress[] resolve(String host) throws UnknownHostException {
            String[] ips = dns.get(host);
            if (ips == null) {
                throw new UnknownHostException(host);
            }
            InetAddress[] out = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) {
                out[i] = InetAddress.getByName(ips[i]);
            }
            return out;
        }
    };

    @Test
    void aPublicHostPasses() {
        assertThatNoException().isThrownBy(() -> guard.check(URI.create("https://public.test/page")));
    }

    @Test
    void aHostNameThatResolvesPrivatelyIsRefused() {
        assertThatThrownBy(() -> guard.check(URI.create("https://internal.test/admin")))
                .isInstanceOf(UnsafeUrlException.class)
                .hasMessage(DestinationGuard.PRIVATE_ADDRESS);
    }

    @Test
    void oneBadAddressAmongSeveralIsEnough() {
        assertThatThrownBy(() -> guard.check(URI.create("http://sneaky.test")))
                .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void literalPrivateAddressesAreRefused() {
        for (String url : new String[]{"http://127.0.0.1:8080/", "http://169.254.169.254/latest/meta-data/",
                "http://[::1]/", "http://localhost/"}) {
            assertThatThrownBy(() -> new DestinationGuard().check(URI.create(url)))
                    .as(url).isInstanceOf(UnsafeUrlException.class);
        }
    }

    @Test
    void otherSchemesAreRefused() {
        assertThatThrownBy(() -> guard.check(URI.create("file:///etc/passwd")))
                .hasMessage("Only http and https links can be shortened.");
        assertThatThrownBy(() -> guard.check(URI.create("ftp://public.test/x")))
                .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void aHostThatDoesNotResolveIsLeftForTheReachabilityCheck() {
        assertThatNoException().isThrownBy(() -> guard.check(URI.create("https://nowhere.test")));
    }
}
