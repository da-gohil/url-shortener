package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
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
    @Autowired UserRepository userRepository;

    private final List<Long> created = new ArrayList<>();
    private final List<Long> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        shortUrlRepository.deleteAllById(created);
        userRepository.deleteAllById(createdUsers);
    }

    @Test
    void userStatsCountOnlyThatUsersLinks() {
        Instant now = Instant.now();
        User owner = saveUser("Stats Test Owner");
        User other = saveUser("Stats Test Other");
        save("itSt01", false, null, owner, 10);
        save("itSt02", true, now.plus(1, ChronoUnit.DAYS), owner, 5);
        save("itSt03", false, now.minus(1, ChronoUnit.DAYS), owner, 2);
        save("itSt04", false, null, other, 100);

        UserUrlStats stats = shortUrlRepository.getUserStats(owner.getId(), now);

        assertThat(stats.totalLinks()).isEqualTo(3);
        assertThat(stats.totalClicks()).isEqualTo(17);
        assertThat(stats.activeLinks()).isEqualTo(2);
        assertThat(stats.expiredLinks()).isEqualTo(1);
    }

    @Test
    void aUserWithNoLinksHasZeroStatsRatherThanNulls() {
        User owner = saveUser("Stats Test Empty");

        UserUrlStats stats = shortUrlRepository.getUserStats(owner.getId(), Instant.now());

        assertThat(stats).isEqualTo(new UserUrlStats(0L, 0L, 0L));
    }

    // --- My URLs filters ------------------------------------------------------------

    @Test
    void filtersOnlyEverSeeTheOwnersLinks() {
        User owner = saveUser("Filter Test Owner");
        User other = saveUser("Filter Test Other");
        save("itF001", false, null, owner, 0);
        save("itF002", false, null, other, 0);

        assertThat(filtered(owner, ShortUrlFilter.NONE)).containsExactly("itF001");
    }

    @Test
    void searchMatchesTheKeyOrTheDestinationIgnoringCase() {
        User owner = saveUser("Filter Test Search");
        save("itF101", false, null, owner, 0, "https://Docs.Example.com/guide");
        save("itF102", false, null, owner, 0, "https://example.com/blog");
        save("itDOCS", false, null, owner, 0, "https://example.com/other");

        assertThat(filtered(owner, ShortUrlFilter.parse("docs", null, null, null)))
                .containsExactlyInAnyOrder("itF101", "itDOCS");
    }

    @Test
    void likeWildcardsInTheSearchAreMatchedLiterally() {
        User owner = saveUser("Filter Test Wildcards");
        save("itF201", false, null, owner, 0, "https://example.com/100%_off");
        save("itF202", false, null, owner, 0, "https://example.com/100xyoff");

        assertThat(filtered(owner, ShortUrlFilter.parse("100%_", null, null, null)))
                .containsExactly("itF201");
    }

    @Test
    void visibilityAndStatusNarrowTheListing() {
        Instant now = Instant.now();
        User owner = saveUser("Filter Test Status");
        save("itF301", false, null, owner, 0);
        save("itF302", true, now.plus(1, ChronoUnit.DAYS), owner, 0);
        save("itF303", false, now.minus(1, ChronoUnit.DAYS), owner, 0);

        assertThat(filtered(owner, ShortUrlFilter.parse(null, "private", null, null)))
                .containsExactly("itF302");
        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, "active", null)))
                .containsExactlyInAnyOrder("itF301", "itF302");
        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, "expired", null)))
                .containsExactly("itF303");
        assertThat(filtered(owner, ShortUrlFilter.parse(null, "public", "active", null)))
                .containsExactly("itF301");
    }

    @Test
    void sortOrdersRankAsLabelled() {
        Instant now = Instant.now();
        User owner = saveUser("Filter Test Sort");
        save("itS001", false, null, owner, 5, null, now.minus(3, ChronoUnit.DAYS));
        save("itS002", false, now.plus(9, ChronoUnit.DAYS), owner, 50, null, now.minus(2, ChronoUnit.DAYS));
        save("itS003", false, now.plus(1, ChronoUnit.DAYS), owner, 1, null, now.minus(1, ChronoUnit.DAYS));

        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, null, "newest")))
                .containsExactly("itS003", "itS002", "itS001");
        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, null, "oldest")))
                .containsExactly("itS001", "itS002", "itS003");
        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, null, "clicks")))
                .containsExactly("itS002", "itS001", "itS003");
        // soonest expiry first; "never expires" goes last
        assertThat(filtered(owner, ShortUrlFilter.parse(null, null, null, "expiry")))
                .containsExactly("itS003", "itS002", "itS001");
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
        return save(shortKey, isPrivate, expiresAt, null, 0);
    }

    private ShortUrl save(String shortKey, boolean isPrivate, Instant expiresAt,
                          User owner, long clicks) {
        return save(shortKey, isPrivate, expiresAt, owner, clicks, null, null);
    }

    private ShortUrl save(String shortKey, boolean isPrivate, Instant expiresAt,
                          User owner, long clicks, String originalUrl) {
        return save(shortKey, isPrivate, expiresAt, owner, clicks, originalUrl, null);
    }

    private ShortUrl save(String shortKey, boolean isPrivate, Instant expiresAt,
                          User owner, long clicks, String originalUrl, Instant createdAt) {
        ShortUrl shortUrl = TestFixtures.shortUrl(null, shortKey, isPrivate, owner);
        shortUrl.setExpiresAt(expiresAt);
        shortUrl.setClickCount(clicks);
        if (originalUrl != null) {
            shortUrl.setOriginalUrl(originalUrl);
        }
        if (createdAt != null) {
            shortUrl.setCreatedAt(createdAt);
        }
        shortUrl = shortUrlRepository.save(shortUrl);
        created.add(shortUrl.getId());
        return shortUrl;
    }

    private List<String> filtered(User owner, ShortUrlFilter filter) {
        var spec = ShortUrlSpecifications.ownedBy(owner.getId())
                .and(ShortUrlSpecifications.matching(filter, Instant.now()));
        return shortUrlRepository.findAll(spec, PageRequest.of(0, 50, filter.sort().toSort()))
                .map(ShortUrl::getShortKey)
                .getContent();
    }

    private User saveUser(String name) {
        User user = userRepository.save(TestFixtures.user(null, name, Role.ROLE_USER));
        createdUsers.add(user.getId());
        return user;
    }

    private long clickCount(ShortUrl shortUrl) {
        return shortUrlRepository.findById(shortUrl.getId()).orElseThrow().getClickCount();
    }
}
