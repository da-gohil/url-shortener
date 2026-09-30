package com.darshangohil.urlshortener.domain.exception;

/** Raised when someone tries to delete a short URL they do not own. */
public class ShortUrlAccessDeniedException extends RuntimeException {
    public ShortUrlAccessDeniedException(String message) {
        super(message);
    }
}
