package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService service;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserService(userRepository, new EntityMapper(), passwordEncoder);
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
