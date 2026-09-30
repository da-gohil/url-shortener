package com.darshangohil.urlshortener.web.utils;

import com.darshangohil.urlshortener.domain.models.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads the current principal out of the security context.
 *
 * <p>The id and name travel on the {@link SecurityUser} itself, so none of this
 * costs a database round trip. Anonymous requests carry an {@code Authentication}
 * whose {@code isAuthenticated()} is {@code true}, so the principal type -- not
 * that flag -- is what tells us whether someone is really logged in.
 */
@Service
public class SecurityUtils {

    public Optional<SecurityUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SecurityUser.from(authentication.getPrincipal()));
    }

    /** The logged-in user's id, or {@code null} for an anonymous visitor. */
    public Long getCurrentUserId() {
        return getCurrentUser().map(SecurityUser::getId).orElse(null);
    }
}
