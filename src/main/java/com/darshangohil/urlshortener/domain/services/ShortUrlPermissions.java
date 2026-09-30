package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Ownership checks used from {@code @PreAuthorize} expressions, e.g.
 * {@code @shortUrlPermissions.ownsAll(#ids, authentication)}.
 *
 * <p>Only ownership lives here. Role checks stay in the expressions themselves, where
 * they go through the role hierarchy.
 */
// named explicitly because the SpEL above refers to it by this name
@Component("shortUrlPermissions")
public class ShortUrlPermissions {

    private final ShortUrlRepository shortUrlRepository;

    public ShortUrlPermissions(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    /** Single-URL form of {@link #ownsAll}. */
    @Transactional(readOnly = true)
    public boolean owns(Long id, Authentication authentication) {
        return ownsAll(List.of(id), authentication);
    }

    /**
     * True if the signed-in user created every one of the given URLs. Anonymously
     * created URLs have no owner, so nobody but an admin can touch them. Ids that do
     * not exist are ignored; deleting them is a no-op anyway.
     */
    @Transactional(readOnly = true)
    public boolean ownsAll(List<Long> ids, Authentication authentication) {
        SecurityUser user = SecurityUser.from(authentication.getPrincipal());
        if (user == null) {
            return false;
        }
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        return shortUrlRepository.findAllByIdIn(ids).stream()
                .allMatch(shortUrl -> shortUrl.getCreatedBy() != null
                        && Objects.equals(shortUrl.getCreatedBy().getId(), user.getId()));
    }
}
