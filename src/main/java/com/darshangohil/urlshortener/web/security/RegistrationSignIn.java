package com.darshangohil.urlshortener.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Signs a user in straight after they register, doing what a successful login form
 * does: a new session id (against session fixation), a new CSRF token, the session
 * recorded in the registry so an admin can still end it, and the security context
 * saved to the session so the next request is signed in.
 *
 * <p>The password was checked moments ago when the account was created, so this
 * loads the account instead of authenticating it again (which would cost a second
 * BCrypt hash).
 */
@Component
public class RegistrationSignIn {

    private final UserDetailsService userDetailsService;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public RegistrationSignIn(UserDetailsService userDetailsService, SessionRegistry sessionRegistry) {
        this.userDetailsService = userDetailsService;
        // the same steps, in the same order, that formLogin uses with session tracking on
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository())));
    }

    public void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
        UserDetails user = userDetailsService.loadUserByUsername(email);
        // keep the password hash out of the session, as the login form does
        if (user instanceof CredentialsContainer credentials) {
            credentials.eraseCredentials();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        authentication.setDetails(detailsSource.buildDetails(request));

        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
