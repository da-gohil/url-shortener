package com.darshangohil.urlshortener.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns away a sign-in attempt while its email or IP is locked, before the password is
 * checked, so a locked-out guesser learns nothing more. Not a Spring bean on purpose:
 * SecurityConfig adds it to the security chain, and as a bean Boot would also register
 * it as a plain servlet filter.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginThrottle loginThrottle;

    public LoginThrottleFilter(LoginThrottle loginThrottle) {
        this.loginThrottle = loginThrottle;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equals(request.getMethod()) && "/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var status = loginThrottle.status(request.getParameter("email"), request.getRemoteAddr());
        if (status.limited()) {
            response.sendRedirect(request.getContextPath() + "/login?locked=" + status.retryAfterMinutes());
            return;
        }
        chain.doFilter(request, response);
    }
}
