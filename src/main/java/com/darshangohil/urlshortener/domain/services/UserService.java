package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.exception.SelfModificationException;
import com.darshangohil.urlshortener.domain.exception.UserNotFoundException;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.UserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationProperties properties;
    private final ActiveSessions activeSessions;

    public UserService(UserRepository userRepository,
                       EntityMapper entityMapper,
                       PasswordEncoder passwordEncoder,
                       ApplicationProperties properties,
                       ActiveSessions activeSessions) {
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.activeSessions = activeSessions;
    }

    /** @param pageNo 1-based, like every other listing */
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResult<UserSummary> findUsers(String query, int pageNo) {
        String search = query == null ? "" : query.strip();
        var pageable = PageRequest.of(Math.max(pageNo, 1) - 1, properties.pageSize(), NEWEST_FIRST);
        return PagedResult.from(userRepository.findUserSummaries(search, pageable), summary -> summary);
    }

    /**
     * Makes a user an admin or an ordinary user. Their sessions end so the new role
     * applies straight away. An admin cannot change their own role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserDto changeRole(Long userId, Role role, Long actingUserId) {
        User user = findOrThrow(userId);
        refuseSelf(userId, actingUserId, "change your own role");
        user.setRole(role);
        activeSessions.endAllFor(user.getEmail());
        return entityMapper.toUserDto(user);
    }

    /**
     * Disables or re-enables an account. Disabling ends the user's sessions and
     * blocks sign-in; their links are left as they are. An admin cannot disable
     * themselves.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserDto setEnabled(Long userId, boolean enabled, Long actingUserId) {
        User user = findOrThrow(userId);
        refuseSelf(userId, actingUserId, enabled ? "re-enable your own account" : "disable your own account");
        user.setEnabled(enabled);
        if (!enabled) {
            activeSessions.endAllFor(user.getEmail());
        }
        return entityMapper.toUserDto(user);
    }

    private static void refuseSelf(Long userId, Long actingUserId, String what) {
        if (Objects.equals(userId, actingUserId)) {
            throw new SelfModificationException("You can't " + what + ". Ask another admin.");
        }
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("No user with id " + id));
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
