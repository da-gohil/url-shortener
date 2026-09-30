package com.darshangohil.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Everything a signed-out visitor is allowed to reach. */
    private static final String[] PUBLIC_PATHS = {
            "/", "/s/**", "/about", "/ping", "/login", "/register",
            "/error", "/favicon.svg", "/styles.css", "/app.js", "/webjars/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                        .permitAll());
        // CSRF stays on: Thymeleaf injects the hidden token into every th:action form.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
