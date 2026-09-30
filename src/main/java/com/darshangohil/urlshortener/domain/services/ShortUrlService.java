package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlAccessDeniedException;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
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

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           EntityMapper entityMapper,
                           ApplicationProperties properties,
                           UrlExistenceValidator urlExistenceValidator, UserRepository userRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.entityMapper = entityMapper;
        this.properties = properties;
        this.urlExistenceValidator = urlExistenceValidator;
        this.userRepository = userRepository;
    }

    public PagedResult<ShortUrlDto> findAllPublicShortUrls(int pageNo) {

        Pageable pageable = PageRequest.of(0,10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ShortUrl> page = shortUrlRepository.findPublicShortUrls(pageRequest(pageNo));

        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    public PagedResult<ShortUrlDto> findUrlsByUser(Long userId, int pageNo) {
        Page<ShortUrl> page = shortUrlRepository.findByCreatedById(userId, pageRequest(pageNo));
        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    public PagedResult<ShortUrlDto> findAllShortUrls(int pageNo) {
        Page<ShortUrl> page = shortUrlRepository.findAllShortUrls(pageRequest(pageNo));
        return PagedResult.from(page, entityMapper::toShortUrlDto);
    }

    /** Callers pass a 1-based page number, matching the {@code ?page=} request parameter. */
    private Pageable pageRequest(int pageNo) {
        int pageIndex = Math.max(pageNo, 1) - 1;
        return PageRequest.of(pageIndex, properties.pageSize(), NEWEST_FIRST);
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
     * <p>Returns empty if the key is unknown, the link has expired, or the link is
     * private and {@code userId} is not its owner. Those three cases deliberately look
     * alike to the caller, so a private key cannot be confirmed by probing for it.
     *
     * @param userId the viewer, or {@code null} for an anonymous visitor
     */
    @Transactional
    public Optional<String> accessOriginalUrl(String shortKey, Long userId) {
        return shortUrlRepository.findByShortKey(shortKey)
                .filter(shortUrl -> shortUrl.getExpiresAt() == null
                        || shortUrl.getExpiresAt().isAfter(Instant.now()))
                .filter(shortUrl -> isVisibleTo(shortUrl, userId))
                .map(shortUrl -> {
                    shortUrl.setClickCount(shortUrl.getClickCount() + 1);
                    shortUrlRepository.save(shortUrl);
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

    /**
     * Deletes the given URLs. A non-admin may only delete their own; if any id falls
     * outside that, nothing is deleted and {@link ShortUrlAccessDeniedException} is raised.
     */
    @Transactional
    public void deleteShortUrls(List<Long> ids, Long userId, boolean isAdmin) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ShortUrl> shortUrls = shortUrlRepository.findAllByIdIn(ids);

        if (!isAdmin) {
            boolean ownsAll = shortUrls.stream().allMatch(shortUrl ->
                    shortUrl.getCreatedBy() != null
                            && Objects.equals(shortUrl.getCreatedBy().getId(), userId));
            if (!ownsAll) {
                throw new ShortUrlAccessDeniedException(
                        "User " + userId + " cannot delete short URLs they do not own");
            }
        }
        shortUrlRepository.deleteAll(shortUrls);
    }

    public static String generateRandomShortKey() {
        StringBuilder sb = new StringBuilder(SHORT_KEY_LENGTH);

        for (int i = 0; i < SHORT_KEY_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
