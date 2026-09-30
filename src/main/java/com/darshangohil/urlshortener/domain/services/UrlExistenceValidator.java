package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;

@Service
public class UrlExistenceValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlExistenceValidator.class);

    private static final int TIMEOUT_MILLIS = 5000;
    private static final int NO_RESPONSE = -1;
    private static final int MAX_REDIRECTS = 5;
    // Java's default "Java/<version>" agent gets turned away by some sites.
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; url-shortener/1.0; +http://localhost:8080)";

    private final DestinationGuard destinationGuard;

    public UrlExistenceValidator(DestinationGuard destinationGuard) {
        this.destinationGuard = destinationGuard;
    }

    /**
     * @throws UnsafeUrlException if the URL, or anywhere it redirects to, points at a
     *         private or local address; see {@link DestinationGuard}
     */
    public boolean isUrlExists(String urlstring) {
        log.debug("Checking if URL exists: {}", urlstring);
        URI uri;
        try {
            uri = new URI(urlstring);
        } catch (Exception e) {
            return false;
        }

        int responseCode = follow(uri, "HEAD");

        // Plenty of sites (LinkedIn among them) answer HEAD with 405/403/501 while
        // serving GET normally, so a HEAD rejection is not evidence the URL is bad.
        if (responseCode == HttpURLConnection.HTTP_BAD_METHOD
                || responseCode == HttpURLConnection.HTTP_FORBIDDEN
                || responseCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
            log.debug("HEAD returned {} for {}, retrying with GET", responseCode, urlstring);
            responseCode = follow(uri, "GET");
        }

        // We can only reject what we know is missing. A server that answered at all proves
        // the host and path resolve; anti-bot defences make anything else ambiguous
        // (LinkedIn serves a non-standard 999 to non-browser clients, for instance), and
        // guessing "broken" there would reject plenty of perfectly good links.
        if (responseCode == NO_RESPONSE) {
            return false;
        }
        return responseCode != HttpURLConnection.HTTP_NOT_FOUND
                && responseCode != HttpURLConnection.HTTP_GONE;
    }

    /**
     * Follows redirects by hand rather than letting HttpURLConnection do it, so every
     * hop passes the guard: a public URL that redirects to 169.254.169.254 is refused
     * rather than fetched.
     */
    private int follow(URI start, String method) {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            destinationGuard.check(current);
            Response response = probe(current, method);
            if (!response.isRedirect()) {
                return response.status();
            }
            try {
                current = current.resolve(response.location());
            } catch (IllegalArgumentException e) {
                log.debug("Unfollowable redirect from {} to {}", current, response.location());
                return NO_RESPONSE;
            }
        }
        log.debug("Too many redirects starting at {}", start);
        return NO_RESPONSE;
    }

    private Response probe(URI uri, String method) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            return new Response(connection.getResponseCode(), connection.getHeaderField("Location"));
        } catch (Exception e) {
            log.warn("{} probe failed for {}: {}", method, uri, e.getMessage());
            return new Response(NO_RESPONSE, null); // host does not resolve, or the URL is malformed
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private record Response(int status, String location) {
        boolean isRedirect() {
            return location != null && (status == 301 || status == 302 || status == 303
                    || status == 307 || status == 308);
        }
    }
}
