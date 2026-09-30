package com.darshangohil.urlshortener.web;

import com.darshangohil.urlshortener.domain.exception.ShortUrlAccessDeniedException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ShortUrlNotFoundException.class)
    ModelAndView handleShortUrlNotFoundException(ShortUrlNotFoundException ex){
        log.warn("Short URL not found: {}", ex.getMessage());
        return new ModelAndView("error/404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ShortUrlAccessDeniedException.class)
    ModelAndView handleShortUrlAccessDenied(ShortUrlAccessDeniedException ex){
        log.warn("Refused short URL deletion: {}", ex.getMessage());
        return new ModelAndView("error/403", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    ModelAndView handleException(Exception ex) throws Exception {
        // Spring Security's filters translate these into a 403 page or a redirect to
        // the login form. Swallowing them here would turn every one into a 500.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        // Spring's own MVC exceptions (unmapped path, bad method, ...) already carry the
        // right status -- don't flatten them all to 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("Request failed with {}: {}", status, ex.getMessage());
            return new ModelAndView(
                    status.isSameCodeAs(HttpStatus.NOT_FOUND) ? "error/404" : "error/500", status);
        }
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return new ModelAndView("error/500", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
