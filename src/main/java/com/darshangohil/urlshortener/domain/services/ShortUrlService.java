package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlNotFoundException;
import com.darshangohil.urlshortener.domain.models.AuditAction;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.OwnerFilter;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.ShortUrlSpecifications;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@Transactional(readOnly = true)
public class ShortUrlService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_KEY_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ShortUrlRepository shortUrlRepository;
    private final EntityMapper entityMapper;
    private final ApplicationProperties properties;
    private final UrlExistenceValidator urlExistenceValidator;
    private final UserRepository userRepository;
    private final AuditLog auditLog;
    private final UrlSafetyPolicy urlSafetyPolicy;

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           EntityMapper entityMapper,
                           ApplicationProperties properties,
                           UrlExistenceValidator urlExistenceValidator, UserRepository userRepository,
                           AuditLog auditLog,
                           UrlSafetyPolicy urlSafetyPolicy) {
        this.shortUrlRepository = shortUrlRepository;
        this.entityMapper = entityMapper;
        this.properties = properties;
        this.urlExistenceValidator = urlExistenceValidator;
        this.userRepository = userRepository;
        this.auditLog = auditLog;
        this.urlSafetyPolicy = urlSafetyPolicy;
    }

    public PagedResult<ShortUrlDto> findAllPublicShortUrls(int pageNo) {
        Page<ShortUrl> page = shortUrlRepository.findActivePublicShortUrls(Instant.now(), pageRequest(pageNo));
        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    /** A user may list their own URLs; an admin may list anyone's. */
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and #userId == principal.id)")
    public PagedResult<ShortUrlDto> findUrlsByUser(Long userId, ShortUrlFilter filter, int pageNo) {
        var spec = ShortUrlSpecifications.ownedBy(userId)
                .and(ShortUrlSpecifications.matching(filter, Instant.now()));
        Page<ShortUrl> page = shortUrlRepository.findAll(spec, pageRequest(pageNo, filter.sort().toSort()));
        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and #userId == principal.id)")
    public UserUrlStats getUserStats(Long userId) {
        return shortUrlRepository.getUserStats(userId, Instant.now());
    }

    /** Every link on the site, for the admin Links tab. */
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResult<ShortUrlDto> findAllShortUrls(ShortUrlFilter filter, OwnerFilter owner, int pageNo) {
        var spec = ShortUrlSpecifications.ownedBy(owner)
                .and(ShortUrlSpecifications.matching(filter, Instant.now()));
        Page<ShortUrl> page = shortUrlRepository.findAll(spec, pageRequest(pageNo, filter.sort().toSort()));
        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    /** Callers pass a 1-based page number, matching the {@code ?page=} request parameter. */
    private Pageable pageRequest(int pageNo) {
        return pageRequest(pageNo, NEWEST_FIRST);
    }

    private Pageable pageRequest(int pageNo, Sort sort) {
        int pageIndex = Math.max(pageNo, 1) - 1;
        return PageRequest.of(pageIndex, properties.pageSize(), sort);
    }

    private String generateUniqueShortKey() {
        String shortKey;
        do {
            shortKey = generateRandomShortKey();
        } while (shortUrlRepository.existsByShortKey(shortKey));
        return shortKey;
    }

    @Transactional
    public ShortUrlDto createShortUrl(CreateShortUrlCmd cmd) {
        urlSafetyPolicy.check(cmd.originalUrl());

        if (properties.validateOriginalUrl()) {
            boolean urlExists = urlExistenceValidator.isUrlExists(cmd.originalUrl());

            if (!urlExists) {
                throw new InvalidUrlException("Could not reach URL: " + cmd.originalUrl());
            }
        }

        var shortKey = generateUniqueShortKey();

        var shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(cmd.originalUrl());
        shortUrl.setShortKey(shortKey);

        if(cmd.userId() == null) {

            shortUrl.setCreatedBy(null);
            shortUrl.setIsPrivate(false);
            shortUrl.setExpiresAt(Instant.now().plus(properties.defaultExpiryInDays(), DAYS));
        }else{
            shortUrl.setCreatedBy(userRepository.findById(cmd.userId()).orElseThrow());
            shortUrl.setIsPrivate(cmd.isPrivate()!= null && cmd.isPrivate());
            shortUrl.setExpiresAt(cmd.expirationInDays() != null ? Instant.now().plus(cmd.expirationInDays(), DAYS) : null);
        }
        shortUrl.setCreatedAt(Instant.now());
        shortUrl.setClickCount(0L);
        shortUrlRepository.save(shortUrl);
        return entityMapper.toShortUrlDto(shortUrl);
    }

    /**
     * Resolves a short key to its original URL, counting the visit.
     *
     * <p>Returns empty if the key is unknown, the link has expired or been disabled, or
     * the link is private and {@code userId} is not its owner. These cases deliberately
     * look alike to the caller, so a private key cannot be confirmed by probing for it.
     *
     * @param userId the viewer, or {@code null} for an anonymous visitor
     */
    @Transactional
    public Optional<String> accessOriginalUrl(String shortKey, Long userId) {
        return shortUrlRepository.findByShortKey(shortKey)
                .filter(shortUrl -> shortUrl.getExpiresAt() == null
                        || shortUrl.getExpiresAt().isAfter(Instant.now()))
                .filter(shortUrl -> !Boolean.TRUE.equals(shortUrl.getDisabled()))
                .filter(shortUrl -> isVisibleTo(shortUrl, userId))
                .map(shortUrl -> {
                    shortUrlRepository.incrementClickCount(shortUrl.getId());
                    return shortUrl.getOriginalUrl();
                });
    }

    /**
     * Public links are visible to everyone. Note the {@code TRUE.equals} rather than a
     * null check: {@code isPrivate} is non-null and {@code false} for a public link, so
     * testing "not null" would lock every link to its creator.
     */
    private boolean isVisibleTo(ShortUrl shortUrl, Long userId) {
        if (!Boolean.TRUE.equals(shortUrl.getIsPrivate())) {
            return true;
        }
        return userId != null
                && shortUrl.getCreatedBy() != null
                && Objects.equals(shortUrl.getCreatedBy().getId(), userId);
    }

    /** One short URL, for its owner or an admin, e.g. to fill in the edit form. */
    @PreAuthorize("hasRole('ADMIN') or @shortUrlPermissions.owns(#id, authentication)")
    public ShortUrlDto getShortUrl(Long id) {
        return entityMapper.toShortUrlDto(findOrThrow(id));
    }

    /**
     * Changes a short URL's visibility and expiry; see {@link UpdateShortUrlCmd}.
     *
     * <p>A link created anonymously has no owner, so it stays public whatever the
     * command says: a private link only resolves for its owner, and this one has none.
     */
    @PreAuthorize("hasRole('ADMIN') or @shortUrlPermissions.owns(#id, authentication)")
    @Transactional
    public ShortUrlDto updateShortUrl(Long id, UpdateShortUrlCmd cmd) {
        ShortUrl shortUrl = findOrThrow(id);
        boolean wasPrivate = Boolean.TRUE.equals(shortUrl.getIsPrivate());
        Instant oldExpiry = shortUrl.getExpiresAt();

        shortUrl.setIsPrivate(shortUrl.getCreatedBy() != null && cmd.isPrivate());
        switch (cmd.expiry()) {
            case KEEP -> { }
            case NEVER -> shortUrl.setExpiresAt(null);
            case DAYS -> shortUrl.setExpiresAt(Instant.now().plus(
                    Objects.requireNonNull(cmd.expirationInDays(), "expirationInDays"), ChronoUnit.DAYS));
        }
        auditLog.record(AuditAction.LINK_EDITED, id,
                describeEdit(shortUrl, wasPrivate, oldExpiry));
        return entityMapper.toShortUrlDto(shortUrl);
    }

    /**
     * Disables or re-enables a link. A disabled link stops redirecting (visitors get
     * the same 404 as an unknown key) but keeps its row, clicks and short key. Owners
     * cannot undo this; only an admin can.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShortUrlDto setDisabled(Long id, boolean disabled) {
        ShortUrl shortUrl = findOrThrow(id);
        shortUrl.setDisabled(disabled);
        auditLog.record(disabled ? AuditAction.LINK_DISABLED : AuditAction.LINK_ENABLED, id,
                shortUrl.getShortKey() + " → " + shortUrl.getOriginalUrl());
        return entityMapper.toShortUrlDto(shortUrl);
    }

    private ShortUrl findOrThrow(Long id) {
        return shortUrlRepository.findById(id)
                .orElseThrow(() -> new ShortUrlNotFoundException("No short URL with id " + id));
    }

    /**
     * Deletes the given URLs. An admin may delete any; a user only their own. If any id
     * falls outside that, nothing is deleted and Spring Security raises
     * {@code AccessDeniedException}, which becomes the 403 page.
     */
    @PreAuthorize("hasRole('ADMIN') or @shortUrlPermissions.ownsAll(#ids, authentication)")
    @Transactional
    public void deleteShortUrls(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ShortUrl> shortUrls = shortUrlRepository.findAllByIdIn(ids);
        // one entry per link; the row is gone afterwards, so the summary keeps its key
        shortUrls.forEach(shortUrl -> auditLog.record(AuditAction.LINK_DELETED, shortUrl.getId(),
                shortUrl.getShortKey() + " → " + shortUrl.getOriginalUrl()));
        shortUrlRepository.deleteAll(shortUrls);
    }

    private static final DateTimeFormatter AUDIT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    /** e.g. "aB3xZ9: public → private; expiry never → 2026-10-07 14:00 UTC". */
    private static String describeEdit(ShortUrl shortUrl, boolean wasPrivate, Instant oldExpiry) {
        List<String> changes = new ArrayList<>();
        boolean isPrivate = Boolean.TRUE.equals(shortUrl.getIsPrivate());
        if (wasPrivate != isPrivate) {
            changes.add(visibility(wasPrivate) + " → " + visibility(isPrivate));
        }
        if (!Objects.equals(oldExpiry, shortUrl.getExpiresAt())) {
            changes.add("expiry " + expiry(oldExpiry) + " → " + expiry(shortUrl.getExpiresAt()));
        }
        return shortUrl.getShortKey() + ": " + (changes.isEmpty() ? "no changes" : String.join("; ", changes));
    }

    private static String visibility(boolean isPrivate) {
        return isPrivate ? "private" : "public";
    }

    private static String expiry(Instant expiresAt) {
        return expiresAt == null ? "never" : AUDIT_TIME.format(expiresAt);
    }

    public static String generateRandomShortKey() {
        StringBuilder sb = new StringBuilder(SHORT_KEY_LENGTH);

        for (int i = 0; i < SHORT_KEY_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
