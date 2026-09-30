package com.darshangohil.urlshortener.domain.services;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Ends a user's live sessions. Their next request lands on /login?expired and they
 * sign in again, which picks up a new role or refuses a disabled account.
 *
 * <p>The registry is in memory, so this covers a single app instance; several
 * instances would need a shared store such as Spring Session.
 */
@Component
public class ActiveSessions {

    private final SessionRegistry sessionRegistry;

    public ActiveSessions(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void endAllFor(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails details
                        && details.getUsername().equalsIgnoreCase(email))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
