package com.darshangohil.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on {@code @PreAuthorize} and defines who inherits what.
 *
 * <p>Kept apart from {@link SecurityConfig} so the service layer's rules can be tested
 * without a web context.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    /**
     * An admin can do anything a user can. Spring Security picks this bean up for both
     * the URL rules in {@link SecurityConfig} and {@code @PreAuthorize} expressions, so
     * {@code hasRole('USER')} is true for an admin everywhere.
     *
     * <p>{@code static} so the method-security infrastructure can read it without
     * initialising this configuration class early.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
    }
}
