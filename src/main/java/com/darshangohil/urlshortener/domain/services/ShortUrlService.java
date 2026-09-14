package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class ShortUrlService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_KEY_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortUrlRepository shortUrlRepository;
    private final EntityMapper entityMapper;
    private final ApplicationProperties properties;
    private final UrlExistenceValidator urlExistenceValidator;

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           EntityMapper entityMapper,
                           ApplicationProperties properties) {
        this.shortUrlRepository = shortUrlRepository;
        this.entityMapper = entityMapper;
        this.properties = properties;
    }

    public List<ShortUrlDto> findAllPublicShortUrls() {
        return shortUrlRepository.findPublicShortUrl()
                .stream().map(entityMapper::toShortUrlDto).toList();
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

        if(properties.validateOriginalUrl()){
            boolean urlExists = urlExistenceValidator.isUrlExists(cmd.originalUrl());

            if(!urlExists){
                throw new RuntimeException("Invalid URL" + cmd.originalUrl());
            }
        }

        var shortKey = generateUniqueShortKey();

        var shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(cmd.originalUrl());
        shortUrl.setShortKey(shortKey);
        shortUrl.setCreatedBy(null);
        shortUrl.setIsPrivate(false);
        shortUrl.setExpiresAt(Instant.now().plus(properties.defaultExpiryInDays(), ChronoUnit.DAYS));
        shortUrl.setCreatedAt(Instant.now());
        shortUrl.setClickCount(0L);
        shortUrlRepository.save(shortUrl);
        return entityMapper.toShortUrlDto(shortUrl);
    }

    /**
     * Resolves a short key to its original URL, counting the visit.
     * Returns empty if the key is unknown or the link has expired.
     */
    @Transactional
    public Optional<String> accessOriginalUrl(String shortKey) {
        return shortUrlRepository.findByShortKey(shortKey)
                .filter(shortUrl -> shortUrl.getExpiresAt() == null
                        || shortUrl.getExpiresAt().isAfter(Instant.now()))
                .map(shortUrl -> {
                    shortUrl.setClickCount(shortUrl.getClickCount() + 1);
                    shortUrlRepository.save(shortUrl);
                    return shortUrl.getOriginalUrl();
                });
    }

    public static String generateRandomShortKey() {
        StringBuilder sb = new StringBuilder(SHORT_KEY_LENGTH);

        for (int i = 0; i < SHORT_KEY_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}