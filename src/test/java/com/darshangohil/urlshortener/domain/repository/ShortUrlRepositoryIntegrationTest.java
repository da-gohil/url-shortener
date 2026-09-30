package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the hand-written queries against the real database. Not {@code @Transactional}:
 * the concurrency test needs committed rows that several threads can see, so every
 * test removes what it created instead of rolling back.
 */
@SpringBootTest
class ShortUrlRepositoryIntegrationTest {

    @Autowired ShortUrlRepository shortUrlRepository;
    @Autowired ShortUrlService shortUrlService;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        shortUrlRepository.deleteAllById(created);
    }

    @Test
    void theHomePageListingSkipsExpiredAndPrivateLinks() {
        Instant now = Instant.now();
        save("itAct1", false, null);
        save("itAct2", false, now.plus(1, ChronoUnit.DAYS));
        save("itExp1", false, now.minus(1, ChronoUnit.DAYS));
        save("itPrv1", true, null);

        List<String> keys = shortUrlRepository
                .findActivePublicShortUrls(now, PageRequest.of(0, 1000))
                .map(ShortUrl::getShortKey)
                .getContent();

        assertThat(keys).contains("itAct1", "itAct2").doesNotContain("itExp1", "itPrv1");
    }

    @Test
    void incrementClickCountAddsOne() {
        ShortUrl shortUrl = save("itClk1", false, null);

        shortUrlRepository.incrementClickCount(shortUrl.getId());
        shortUrlRepository.incrementClickCount(shortUrl.getId());

        assertThat(clickCount(shortUrl)).isEqualTo(2L);
    }

    @Test
    void concurrentVisitsAreAllCounted() throws Exception {
        ShortUrl shortUrl = save("itClk2", false, null);
        int visits = 20;
        ExecutorService pool = Executors.newFixedThreadPool(visits);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < visits; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return shortUrlService.accessOriginalUrl("itClk2", null);
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }

        // a read-increment-save would lose updates here and land short of 20
        assertThat(clickCount(shortUrl)).isEqualTo(visits);
    }

    private ShortUrl save(String shortKey, boolean isPrivate, Instant expiresAt) {
        ShortUrl shortUrl = TestFixtures.shortUrl(null, shortKey, isPrivate, null);
        shortUrl.setExpiresAt(expiresAt);
        shortUrl = shortUrlRepository.save(shortUrl);
        created.add(shortUrl.getId());
        return shortUrl;
    }

    private long clickCount(ShortUrl shortUrl) {
        return shortUrlRepository.findById(shortUrl.getId()).orElseThrow().getClickCount();
    }
}
