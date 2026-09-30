package com.darshangohil.urlshortener.domain.models;

import java.time.OffsetDateTime;

/** One row of the admin Users tab. */
public record UserSummary(Long id, String name, String email, Role role, Boolean enabled,
                          OffsetDateTime createdAt, Long linkCount) {

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }
}
