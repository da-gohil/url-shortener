package com.darshangohil.urlshortener.domain.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@Service
public class UrlExistenceValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlExistenceValidator.class);

    private static final int TIMEOUT_MILLIS = 5000;
    private static final int NO_RESPONSE = -1;
    // Java's default "Java/<version>" agent gets turned away by some sites.
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; url-shortener/1.0; +http://localhost:8080)";

    public boolean isUrlExists(String urlstring) {
        log.debug("Checking if URL exists: {}", urlstring);

        int responseCode = probe(urlstring, "HEAD");

        // Plenty of sites (LinkedIn among them) answer HEAD with 405/403/501 while
        // serving GET normally, so a HEAD rejection is not evidence the URL is bad.
        if (responseCode == HttpURLConnection.HTTP_BAD_METHOD
                || responseCode == HttpURLConnection.HTTP_FORBIDDEN
                || responseCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
            log.debug("HEAD returned {} for {}, retrying with GET", responseCode, urlstring);
            responseCode = probe(urlstring, "GET");
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

    /** Returns the HTTP status for the given method, or {@link #NO_RESPONSE} if unreachable. */
    private int probe(String urlstring, String method) {
        HttpURLConnection connection = null;
        try {
            URL url = new URI(urlstring).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            return connection.getResponseCode();
        } catch (Exception e) {
            log.warn("{} probe failed for {}: {}", method, urlstring, e.getMessage());
            return NO_RESPONSE; // host does not resolve, or the URL is malformed
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
