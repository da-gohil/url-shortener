package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.OwnerFilter;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises the {@code @PreAuthorize} rules on {@link ShortUrlService} through a real
 * method-security proxy and the real role hierarchy. The repositories are mocks, so
 * this needs no database or web layer.
 */
@SpringJUnitConfig(ShortUrlServiceSecurityTest.Config.class)
class ShortUrlServiceSecurityTest {

    @Configuration
    @Import({MethodSecurityConfig.class, ShortUrlService.class, ShortUrlPermissions.class,
            EntityMapper.class, AdminOverviewService.class})
    static class Config {
        @Bean
        ApplicationProperties applicationProperties() {
            return new ApplicationProperties("http://localhost:8080", 30, false, 10);
        }
    }

    @Autowired ShortUrlService service;
    @Autowired AdminOverviewService overviewService;
    @Autowired RoleHierarchy roleHierarchy;
    @MockitoBean ShortUrlRepository shortUrlRepository;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AuditLog auditLog;
    @MockitoBean UrlExistenceValidator urlExistenceValidator;

    private final User admin = TestFixtures.user(1L, "Admin User", Role.ROLE_ADMIN);
    private final User owner = TestFixtures.user(2L, "John Doe", Role.ROLE_USER);
    private final User stranger = TestFixtures.user(3L, "Jane Smith", Role.ROLE_USER);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // --- role hierarchy -------------------------------------------------------------

    @Test
    void anAdminHoldsEveryUserPermission() {
        assertThat(roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void aUserDoesNotInheritAdmin() {
        assertThat(roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_USER"))))
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    // --- listing every URL ----------------------------------------------------------

    @Test
    void anAdminCanListEveryUrl() {
        signInAs(admin);
        given(shortUrlRepository.findAll(anySpec(), any(Pageable.class))).willReturn(Page.empty());

        assertThatNoException().isThrownBy(() -> service.findAllShortUrls(ShortUrlFilter.NONE, OwnerFilter.ANYONE, 1));
    }

    @Test
    void aUserCannotListEveryUrl() {
        signInAs(owner);

        assertThatThrownBy(() -> service.findAllShortUrls(ShortUrlFilter.NONE, OwnerFilter.ANYONE, 1))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- listing one user's URLs ----------------------------------------------------

    @Test
    void aUserCanListTheirOwnUrls() {
        signInAs(owner);
        given(shortUrlRepository.findAll(anySpec(), any(Pageable.class))).willReturn(Page.empty());

        assertThatNoException().isThrownBy(() -> service.findUrlsByUser(owner.getId(), ShortUrlFilter.NONE, 1));
    }

    @Test
    void aUserCannotListSomeoneElsesUrls() {
        signInAs(owner);

        assertThatThrownBy(() -> service.findUrlsByUser(stranger.getId(), ShortUrlFilter.NONE, 1))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminCanListAnyUsersUrls() {
        signInAs(admin);
        given(shortUrlRepository.findAll(anySpec(), any(Pageable.class))).willReturn(Page.empty());

        assertThatNoException().isThrownBy(() -> service.findUrlsByUser(stranger.getId(), ShortUrlFilter.NONE, 1));
    }

    // --- stats ----------------------------------------------------------------------

    @Test
    void aUserCanSeeTheirOwnStats() {
        signInAs(owner);

        assertThatNoException().isThrownBy(() -> service.getUserStats(owner.getId()));
    }

    @Test
    void aUserCannotSeeSomeoneElsesStats() {
        signInAs(owner);

        assertThatThrownBy(() -> service.getUserStats(stranger.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminCanSeeAnyUsersStats() {
        signInAs(admin);

        assertThatNoException().isThrownBy(() -> service.getUserStats(stranger.getId()));
    }

    // --- editing ---------------------------------------------------------------------

    @Test
    void anOwnerCanOpenAndEditTheirLink() {
        signInAs(owner);
        givenLink(TestFixtures.shortUrl(1L, "mine01", false, owner));

        assertThatNoException().isThrownBy(() -> service.getShortUrl(1L));
        assertThatNoException().isThrownBy(() -> service.updateShortUrl(1L, KEEP_ALL));
    }

    @Test
    void aUserCannotOpenOrEditSomeoneElsesLink() {
        signInAs(owner);
        givenLink(TestFixtures.shortUrl(1L, "their1", false, stranger));

        assertThatThrownBy(() -> service.getShortUrl(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateShortUrl(1L, KEEP_ALL)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminCanEditAnyLink() {
        signInAs(admin);
        givenLink(TestFixtures.shortUrl(1L, "guest1", false, null));

        assertThatNoException().isThrownBy(() -> service.updateShortUrl(1L, KEEP_ALL));
    }

    // --- overview --------------------------------------------------------------------

    @Test
    void onlyAnAdminCanSeeTheOverview() {
        signInAs(owner);

        assertThatThrownBy(() -> overviewService.getOverview()).isInstanceOf(AccessDeniedException.class);
    }

    // --- disabling -------------------------------------------------------------------

    @Test
    void onlyAnAdminCanDisableALink() {
        givenLink(TestFixtures.shortUrl(1L, "mine01", false, owner));

        signInAs(owner);
        assertThatThrownBy(() -> service.setDisabled(1L, false)).isInstanceOf(AccessDeniedException.class);

        signInAs(admin);
        assertThatNoException().isThrownBy(() -> service.setDisabled(1L, true));
    }

    // --- deleting -------------------------------------------------------------------

    @Test
    void anOwnerCanDeleteTheirOwnUrls() {
        signInAs(owner);
        var mine = TestFixtures.shortUrl(1L, "mine01", false, owner);
        given(shortUrlRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(mine));

        service.deleteShortUrls(List.of(1L));

        verify(shortUrlRepository).deleteAll(List.of(mine));
    }

    @Test
    void aUserCannotDeleteSomeoneElsesUrl() {
        signInAs(owner);
        given(shortUrlRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(
                TestFixtures.shortUrl(1L, "their1", false, stranger)));

        assertThatThrownBy(() -> service.deleteShortUrls(List.of(1L)))
                .isInstanceOf(AccessDeniedException.class);
        verify(shortUrlRepository, never()).deleteAll(anyList());
    }

    @Test
    void aMixedBatchDeletesNothing() {
        signInAs(owner);
        given(shortUrlRepository.findAllByIdIn(List.of(1L, 2L))).willReturn(List.of(
                TestFixtures.shortUrl(1L, "mine01", false, owner),
                TestFixtures.shortUrl(2L, "their1", false, stranger)));

        assertThatThrownBy(() -> service.deleteShortUrls(List.of(1L, 2L)))
                .isInstanceOf(AccessDeniedException.class);
        verify(shortUrlRepository, never()).deleteAll(anyList());
    }

    @Test
    void aUserCannotDeleteAnAnonymouslyCreatedUrl() {
        signInAs(owner);
        given(shortUrlRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(
                TestFixtures.shortUrl(1L, "guest1", false, null)));

        assertThatThrownBy(() -> service.deleteShortUrls(List.of(1L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminCanDeleteAnyUrl() {
        signInAs(admin);
        var theirs = TestFixtures.shortUrl(1L, "their1", false, stranger);
        var guest = TestFixtures.shortUrl(2L, "guest1", false, null);
        given(shortUrlRepository.findAllByIdIn(List.of(1L, 2L))).willReturn(List.of(theirs, guest));

        service.deleteShortUrls(List.of(1L, 2L));

        verify(shortUrlRepository).deleteAll(List.of(theirs, guest));
    }

    private static final UpdateShortUrlCmd KEEP_ALL =
            new UpdateShortUrlCmd(false, UpdateShortUrlCmd.Expiry.KEEP, null);

    private void givenLink(ShortUrl shortUrl) {
        given(shortUrlRepository.findAllByIdIn(List.of(shortUrl.getId()))).willReturn(List.of(shortUrl));
        given(shortUrlRepository.findById(shortUrl.getId())).willReturn(java.util.Optional.of(shortUrl));
    }

    @SuppressWarnings("unchecked")
    private static Specification<ShortUrl> anySpec() {
        return any(Specification.class);
    }

    private void signInAs(User user) {
        var principal = TestFixtures.principal(user.getId(), user.getName(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }
}
