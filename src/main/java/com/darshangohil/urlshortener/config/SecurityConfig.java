package com.darshangohil.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.http.HttpMethod;
import com.darshangohil.urlshortener.web.security.LoginThrottle;
import com.darshangohil.urlshortener.web.security.LoginThrottleFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Only this site's own scripts may run, and nothing loads from elsewhere. Inline
     * {@code style} attributes are allowed (the chart's bar heights use them); inline
     * scripts are not, which is why behaviour lives in /app.js. frame-ancestors 'none'
     * stops the site being framed for clickjacking.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** Everything a signed-out visitor is allowed to reach. */
    private static final String[] PUBLIC_PATHS = {
            "/", "/s/**", "/about", "/ping", "/login", "/register",
            "/error", "/favicon.svg", "/styles.css", "/app.js", "/webjars/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginThrottle loginThrottle) throws Exception {
        http
                .addFilterBefore(new LoginThrottleFilter(loginThrottle), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // anonymous visitors may still shorten a URL; they just get a
                        // public link with the default expiry
                        .requestMatchers(HttpMethod.POST, "/short-urls").permitAll()
                        // hasRole goes through the role hierarchy (MethodSecurityConfig),
                        // so an admin also passes every hasRole("USER") rule
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/my-urls", "/my-urls/**", "/delete-urls").hasRole("USER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/my-urls", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        .permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                // track sessions so an admin's change to an account (disable, role) can
                // end that user's sessions at once instead of at their next login
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?expired"));
        // CSRF stays on: Thymeleaf injects the hidden token into every th:action form.
        return http.build();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when sessions end (logout, timeout), so it doesn't grow forever. */
    @Bean
    static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
