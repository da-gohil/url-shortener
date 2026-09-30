package com.darshangohil.urlshortener.web.dtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateShortUrlForm(
    @NotBlank(message= "Original URL is required")
    // blank is allowed through here so @NotBlank owns that message on its own
    @URL(regexp = "(?i)^(|https?://.*)$", message = "Enter a valid http(s) URL")
    String originalUrl,
    Boolean isPrivate,
    @Min(1)
    @Max(365)
    Integer expirationInDays){

    // Runs at form-binding time, before validation
    public CreateShortUrlForm {
        originalUrl = normalize(originalUrl);
    }

    // Optional no-arg canonical constructor fallback for Spring Form binding
    public CreateShortUrlForm() {
        this("", null, null);
    }

    // Convenience constructor: a URL on its own, with the optional fields left unset
    public CreateShortUrlForm(String originalUrl) {
        this(originalUrl, null, null);
    }


    private static String normalize(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.*")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }
}
