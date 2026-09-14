package com.darshangohil.urlshortener.domain.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UrlExistenceValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlExistenceValidator.class);

    public static boolean isUrlExists(String urlstring) {
        try {
            log.debug("Checking if URL exists: {}", urlstring);
            URL url = new URI(urlstring).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            // 2xx and 3xx are valid
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            log.error("Error while checking URL: {}", urlstring, e);
            return false; // URL is invalid or not reachable
        }
    }
}