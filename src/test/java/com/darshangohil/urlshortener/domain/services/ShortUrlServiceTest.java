package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShortUrlServiceTest {

    private ShortUrlRepository shortUrlRepository;
    private ShortUrlService service;

    private final User owner = TestFixtures.user(2L, "John Doe", Role.ROLE_USER);
    private final User stranger = TestFixtures.user(3L, "Jane Smith", Role.ROLE_USER);

    @BeforeEach
    void setUp() {
        shortUrlRepository = mock(ShortUrlRepository.class);
        // validateOriginalUrl off, so these tests never touch the network
        var properties = new ApplicationProperties("http://localhost:8080", 30, false, 10);
        service = new ShortUrlService(shortUrlRepository, new EntityMapper(), properties,
                mock(UrlExistenceValidator.class), mock(UserRepository.class));
    }

    @Test
    void publicUrlResolvesForAnAnonymousVisitor() {
        givenStored(TestFixtures.shortUrl(1L, "pub001", false, owner));

        assertThat(service.accessOriginalUrl("pub001", null))
                .contains("https://example.com/pub001");
    }

    @Test
    void publicUrlResolvesForSomeoneOtherThanItsOwner() {
        // regression guard: the privacy check must key off isPrivate being TRUE, not
        // merely non-null, or every public link would lock to its creator
        givenStored(TestFixtures.shortUrl(1L, "pub001", false, owner));

        assertThat(service.accessOriginalUrl("pub001", stranger.getId()))
                .contains("https://example.com/pub001");
    }

    @Test
    void privateUrlResolvesForItsOwner() {
        givenStored(TestFixtures.shortUrl(1L, "prv001", true, owner));

        assertThat(service.accessOriginalUrl("prv001", owner.getId()))
                .contains("https://example.com/prv001");
    }

    @Test
    void privateUrlIsHiddenFromAnAnonymousVisitor() {
        givenStored(TestFixtures.shortUrl(1L, "prv001", true, owner));

        assertThat(service.accessOriginalUrl("prv001", null)).isEmpty();
    }

    @Test
    void privateUrlIsHiddenFromAnotherSignedInUser() {
        givenStored(TestFixtures.shortUrl(1L, "prv001", true, owner));

        assertThat(service.accessOriginalUrl("prv001", stranger.getId())).isEmpty();
    }

    @Test
    void aHiddenPrivateUrlIsNotCountedAsAClick() {
        var shortUrl = TestFixtures.shortUrl(1L, "prv001", true, owner);
        givenStored(shortUrl);

        service.accessOriginalUrl("prv001", stranger.getId());

        assertThat(shortUrl.getClickCount()).isZero();
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void aResolvedUrlCountsTheClick() {
        var shortUrl = TestFixtures.shortUrl(1L, "pub001", false, owner);
        givenStored(shortUrl);

        service.accessOriginalUrl("pub001", null);

        assertThat(shortUrl.getClickCount()).isEqualTo(1L);
        verify(shortUrlRepository).save(shortUrl);
    }

    @Test
    void expiredUrlDoesNotResolve() {
        var shortUrl = TestFixtures.shortUrl(1L, "old001", false, owner);
        shortUrl.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        givenStored(shortUrl);

        assertThat(service.accessOriginalUrl("old001", owner.getId())).isEmpty();
    }

    @Test
    void unknownKeyDoesNotResolve() {
        given(shortUrlRepository.findByShortKey("nope00")).willReturn(Optional.empty());

        assertThat(service.accessOriginalUrl("nope00", owner.getId())).isEmpty();
    }

    @Test
    void deleteRemovesTheGivenUrls() {
        // who may delete what is enforced by @PreAuthorize; see ShortUrlServiceSecurityTest
        var mine = TestFixtures.shortUrl(1L, "mine01", false, owner);
        given(shortUrlRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(mine));

        service.deleteShortUrls(List.of(1L));

        verify(shortUrlRepository).deleteAll(List.of(mine));
    }

    @Test
    void deletingNothingTouchesNothing() {
        service.deleteShortUrls(List.of());

        verify(shortUrlRepository, never()).deleteAll(any());
    }

    @Test
    void generatedKeysAreSixUrlSafeCharacters() {
        assertThat(ShortUrlService.generateRandomShortKey())
                .hasSize(6)
                .matches("[A-Za-z0-9]{6}");
    }

    private void givenStored(ShortUrl shortUrl) {
        given(shortUrlRepository.findByShortKey(shortUrl.getShortKey()))
                .willReturn(Optional.of(shortUrl));
    }
}
