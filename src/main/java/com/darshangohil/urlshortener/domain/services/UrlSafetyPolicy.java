package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.UrlSafetyProperties;
import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * What may be shortened at all, checked for every new link whether or not the
 * reachability check is switched on. Cheap rules first; the network lookup last.
 */
@Component
public class UrlSafetyPolicy {

    /** host:port of this site, the port filled in from the scheme when not given. */
    private final String ownAuthority;
    private final List<String> blockedHosts;
    private final SafeBrowsingClient safeBrowsing;

    public UrlSafetyPolicy(ApplicationProperties applicationProperties,
                           UrlSafetyProperties safetyProperties,
                           SafeBrowsingClient safeBrowsing) {
        this.ownAuthority = authorityOf(URI.create(applicationProperties.baseUrl()));
        this.blockedHosts = safetyProperties.blockedHosts().stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();
        this.safeBrowsing = safeBrowsing;
    }

    /** @throws UnsafeUrlException with a message for the person submitting the link */
    public void check(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new UnsafeUrlException("That doesn't look like a valid link.");
        }
        if (uri.getRawUserInfo() != null) {
            // https://yourbank.com@evil.example goes to evil.example
            throw new UnsafeUrlException("Links with a username or password in them can't be shortened.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        // host and port: localhost:5432 is a different service from a site on localhost:8080
        if (!host.isEmpty() && authorityOf(uri).equals(ownAuthority)) {
            throw new UnsafeUrlException("That's already a link on this site, so there's nothing to shorten.");
        }
        if (blockedHosts.stream().anyMatch(blocked -> host.equals(blocked) || host.endsWith("." + blocked))) {
            throw new UnsafeUrlException("Links to that site can't be shortened here.");
        }
        if (safeBrowsing.isFlagged(url)) {
            throw new UnsafeUrlException("Google Safe Browsing reports that site as unsafe, so it can't be shortened.");
        }
    }

    private static String authorityOf(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return host + ":" + port;
    }
}
