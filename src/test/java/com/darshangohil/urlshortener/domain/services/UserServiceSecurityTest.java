package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every admin-only UserService method refuses an ordinary user. */
@SpringJUnitConfig(UserServiceSecurityTest.Config.class)
class UserServiceSecurityTest {

    @Configuration
    @Import({MethodSecurityConfig.class, UserService.class, EntityMapper.class})
    static class Config {
        @Bean
        ApplicationProperties applicationProperties() {
            return new ApplicationProperties("http://localhost:8080", 30, false, 10);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired UserService service;
    @MockitoBean UserRepository userRepository;
    @MockitoBean ActiveSessions activeSessions;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anOrdinaryUserCannotManageUsers() {
        var john = TestFixtures.principal(2L, "John Doe", Role.ROLE_USER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(john, null, john.getAuthorities()));

        assertThatThrownBy(() -> service.findUsers(null, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findUser(3L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.changeRole(2L, Role.ROLE_ADMIN, 2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setEnabled(3L, false, 2L)).isInstanceOf(AccessDeniedException.class);
    }
}
