package com.darshangohil.urlshortener.domain.exception;

/**
 * An admin tried to demote or disable their own account. Refused so the site can
 * never be left with no admin able to undo it.
 */
public class SelfModificationException extends RuntimeException {
    public SelfModificationException(String message) {
        super(message);
    }
}
