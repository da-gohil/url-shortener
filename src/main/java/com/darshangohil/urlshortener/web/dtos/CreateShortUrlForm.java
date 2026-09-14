package com.darshangohil.urlshortener.web.dtos;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateShortUrlForm(
    @NotBlank(message= "Original URL is required")
    // blank is allowed through here so @NotBlank owns that message on its own
    @URL(regexp = "(?i)^(|https?://.*)$", message = "Enter a valid http(s) URL")
    String originalUrl){

    // Runs at form-binding time, before validation, so a user typing
    // "www.facebook.com" is treated as "https://www.facebook.com".
    public CreateShortUrlForm {
        originalUrl = normalize(originalUrl);
    }

    // Optional no-arg canonical constructor fallback for Spring Form binding
    public CreateShortUrlForm() {
        this("");
    }

    private static String normalize(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        // Anything already carrying a scheme is left untouched, so ftp:// and
        // javascript: still fail validation instead of being rewritten into https.
        // Leading "/" is left alone too, so relative paths stay invalid.
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.*")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }
}
