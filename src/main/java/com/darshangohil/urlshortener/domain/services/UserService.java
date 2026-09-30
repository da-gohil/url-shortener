package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       EntityMapper entityMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<UserDto> findUser(Long id) {
        return userRepository.findById(id).map(entityMapper::toUserDto);
    }

    @Transactional
    public UserDto registerUser(CreateUserCmd cmd) {
        // emails are matched case-insensitively at login, so store them folded
        String email = cmd.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email already registered: " + email);
        }

        var user = new User();
        user.setName(cmd.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(cmd.password()));
        user.setRole(Role.ROLE_USER);
        user.setCreatedAt(OffsetDateTime.now());

        userRepository.save(user);
        return entityMapper.toUserDto(user);
    }
}
