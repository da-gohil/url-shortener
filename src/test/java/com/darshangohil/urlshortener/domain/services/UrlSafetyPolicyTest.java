package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.UrlSafetyProperties;
import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UrlSafetyPolicyTest {

    private SafeBrowsingClient safeBrowsing;
    private UrlSafetyPolicy policy;

    @BeforeEach
    void setUp() {
        safeBrowsing = mock(SafeBrowsingClient.class);
        policy = new UrlSafetyPolicy(
                new ApplicationProperties("https://sho.rt", 30, false, 10),
                new UrlSafetyProperties(List.of("bad.example", " Evil.Test "), ""),
                safeBrowsing);
    }

    @Test
    void anOrdinaryLinkPasses() {
        assertThatNoException().isThrownBy(() -> policy.check("https://example.com/page?x=1"));
    }

    @Test
    void credentialsInTheLinkAreRefused() {
        assertThatThrownBy(() -> policy.check("https://yourbank.com@evil.example/login"))
                .isInstanceOf(UnsafeUrlException.class)
                .hasMessageContaining("username or password");
        assertThatThrownBy(() -> policy.check("https://user:pass@example.com/"))
                .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void linksBackToThisSiteAreRefused() {
        assertThatThrownBy(() -> policy.check("https://SHO.RT/s/abc123"))
                .hasMessageContaining("already a link on this site");
    }

    @Test
    void theSelfLinkRuleComparesThePortToo() {
        // same host, explicit default port: still this site
        assertThatThrownBy(() -> policy.check("https://sho.rt:443/s/abc123")).isInstanceOf(UnsafeUrlException.class);
        // same host, another port: a different service, not a self-link
        assertThatNoException().isThrownBy(() -> policy.check("https://sho.rt:8443/page"));
        assertThatNoException().isThrownBy(() -> policy.check("http://sho.rt/page"));
    }

    @Test
    void blockedHostsAndTheirSubdomainsAreRefusedButLookalikesAreNot() {
        assertThatThrownBy(() -> policy.check("https://bad.example/x")).hasMessageContaining("can't be shortened here");
        assertThatThrownBy(() -> policy.check("https://www.bad.example/x")).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> policy.check("http://EVIL.test")).isInstanceOf(UnsafeUrlException.class);
        // a different domain that merely ends in the same letters
        assertThatNoException().isThrownBy(() -> policy.check("https://notbad.example/x"));
    }

    @Test
    void aSiteFlaggedBySafeBrowsingIsRefused() {
        given(safeBrowsing.isFlagged("https://malware.example/")).willReturn(true);

        assertThatThrownBy(() -> policy.check("https://malware.example/"))
                .hasMessageContaining("Google Safe Browsing");
    }

    @Test
    void theNetworkLookupIsSkippedWhenACheapRuleAlreadyRefused() {
        assertThatThrownBy(() -> policy.check("https://bad.example/"));

        verify(safeBrowsing, never()).isFlagged(anyString());
    }
}
