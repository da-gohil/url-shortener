package com.darshangohil.urlshortener;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Rules for which destinations may be shortened.
 *
 * @param blockedHosts        hosts (and their subdomains) that can never be shortened
 * @param safeBrowsingApiKey  a Google Safe Browsing v4 key; blank turns the lookup off
 */
@ConfigurationProperties(prefix = "app.url-safety")
public record UrlSafetyProperties(
        @DefaultValue List<String> blockedHosts,
        @DefaultValue("") String safeBrowsingApiKey) {

    public boolean safeBrowsingEnabled() {
        return safeBrowsingApiKey != null && !safeBrowsingApiKey.isBlank();
    }
}
