package com.darshangohil.urlshortener.domain.models;

import com.darshangohil.urlshortener.domain.entities.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * Authenticated principal. Carries the database id and display name alongside the
 * credentials so the rest of the app can identify the user without a second query.
 */
public class SecurityUser extends org.springframework.security.core.userdetails.User {

    private final Long id;
    private final String name;

    public SecurityUser(User user) {
        super(user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
        this.id = user.getId();
        this.name = user.getName();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Narrows an arbitrary principal, which may be the anonymous one, to a real user. */
    public static SecurityUser from(Object principal) {
        return principal instanceof SecurityUser securityUser ? securityUser : null;
    }

    public static boolean isRealUser(UserDetails userDetails) {
        return userDetails instanceof SecurityUser;
    }
}
