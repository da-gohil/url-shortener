package com.darshangohil.urlshortener.domain.exception;

/**
 * A destination the shortener refuses to link to. The message is written for the
 * person who submitted the URL and is shown on the form as-is.
 */
public class UnsafeUrlException extends RuntimeException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}
