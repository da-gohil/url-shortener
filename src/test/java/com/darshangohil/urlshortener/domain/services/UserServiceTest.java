package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.exception.SelfModificationException;
import com.darshangohil.urlshortener.domain.exception.UserNotFoundException;
import com.darshangohil.urlshortener.support.TestFixtures;
import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserServiceTest {

    private UserRepository userRepository;
    private ActiveSessions activeSessions;
    private UserService service;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        activeSessions = mock(ActiveSessions.class);
        var properties = new ApplicationProperties("http://localhost:8080", 30, false, 10);
        service = new UserService(userRepository, new EntityMapper(), passwordEncoder,
                properties, activeSessions);
    }

    // --- admin: roles and accounts (who may call these: UserServiceSecurityTest) -------

    @Test
    void changingARoleSavesItAndSignsTheUserOut() {
        User john = givenUser(2L, "John Doe", Role.ROLE_USER);

        service.changeRole(2L, Role.ROLE_ADMIN, 1L);

        assertThat(john.getRole()).isEqualTo(Role.ROLE_ADMIN);
        verify(activeSessions).endAllFor("john.doe@example.com");
    }

    @Test
    void disablingAnAccountSignsTheUserOut() {
        User john = givenUser(2L, "John Doe", Role.ROLE_USER);

        service.setEnabled(2L, false, 1L);

        assertThat(john.getEnabled()).isFalse();
        verify(activeSessions).endAllFor("john.doe@example.com");
    }

    @Test
    void reEnablingLeavesSessionsAlone() {
        User john = givenUser(2L, "John Doe", Role.ROLE_USER);
        john.setEnabled(false);

        service.setEnabled(2L, true, 1L);

        assertThat(john.getEnabled()).isTrue();
        verify(activeSessions, never()).endAllFor(any());
    }

    @Test
    void anAdminCannotDemoteOrDisableThemselves() {
        User admin = givenUser(1L, "Admin User", Role.ROLE_ADMIN);

        assertThatThrownBy(() -> service.changeRole(1L, Role.ROLE_USER, 1L))
                .isInstanceOf(SelfModificationException.class);
        assertThatThrownBy(() -> service.setEnabled(1L, false, 1L))
                .isInstanceOf(SelfModificationException.class);

        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(admin.getEnabled()).isTrue();
        verify(activeSessions, never()).endAllFor(any());
    }

    @Test
    void changingAnUnknownUserIsNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(99L, false, 1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User givenUser(Long id, String name, Role role) {
        User user = TestFixtures.user(id, name, role);
        given(userRepository.findById(id)).willReturn(Optional.of(user));
        return user;
    }

    @Test
    void registrationStoresAHashedPasswordAndTheUserRole() {
        given(userRepository.existsByEmailIgnoreCase(any())).willReturn(false);

        service.registerUser(new CreateUserCmd("New User", "New.User@Example.com", "s3cretpassword"));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("New User");
        // emails are folded so sign-in is not case sensitive
        assertThat(saved.getEmail()).isEqualTo("new.user@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getPassword()).isNotEqualTo("s3cretpassword");
        assertThat(passwordEncoder.matches("s3cretpassword", saved.getPassword())).isTrue();
    }

    @Test
    void registrationNeverGrantsAdmin() {
        given(userRepository.existsByEmailIgnoreCase(any())).willReturn(false);

        service.registerUser(new CreateUserCmd("Sneaky", "sneaky@example.com", "s3cretpassword"));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isNotEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    void duplicateEmailIsRejected() {
        given(userRepository.existsByEmailIgnoreCase("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.registerUser(
                new CreateUserCmd("Dup", "  TAKEN@example.com ", "s3cretpassword")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }
}
