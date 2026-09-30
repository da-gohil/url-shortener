package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.UrlSafetyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Asks Google Safe Browsing (v4 Lookup API) whether a URL is known malware or phishing.
 * Does nothing unless {@code app.url-safety.safe-browsing-api-key} is set.
 */
@Component
public class SafeBrowsingClient {

    private static final Logger log = LoggerFactory.getLogger(SafeBrowsingClient.class);
    static final String ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find";

    private final RestClient restClient;
    private final UrlSafetyProperties properties;

    // two constructors, so Spring needs telling which one to use
    @Autowired
    public SafeBrowsingClient(UrlSafetyProperties properties) {
        this(RestClient.builder(), properties);
    }

    /** Takes a builder so tests can bind a mock server to it. */
    SafeBrowsingClient(RestClient.Builder builder, UrlSafetyProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.safeBrowsingEnabled();
    }

    /**
     * True only if Google reports a match. If the API can't be reached or errors, the
     * link is allowed (logged as a warning): an outage at Google shouldn't stop
     * everyone creating links.
     */
    public boolean isFlagged(String url) {
        if (!isEnabled()) {
            return false;
        }
        Map<String, Object> body = Map.of(
                "client", Map.of("clientId", "url-shortener", "clientVersion", "1.0"),
                "threatInfo", Map.of(
                        "threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING",
                                "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", List.of(Map.of("url", url))));
        try {
            Map<?, ?> response = restClient.post()
                    .uri(ENDPOINT + "?key={key}", properties.safeBrowsingApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            // an empty object means no match; otherwise there's a non-empty "matches" list
            return response != null && response.get("matches") instanceof List<?> matches && !matches.isEmpty();
        } catch (RestClientException e) {
            log.warn("Safe Browsing lookup failed for {}, allowing it: {}", url, e.getMessage());
            return false;
        }
    }
}
