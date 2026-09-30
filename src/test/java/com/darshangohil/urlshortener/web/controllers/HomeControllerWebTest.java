package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.support.TestFixtures;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(HomeController.class)
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, SecurityUtils.class})
@TestPropertySource(properties = "app.baseUrl=http://localhost:8080")
class HomeControllerWebTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ShortUrlService shortUrlService;

    @BeforeEach
    void stubStats() {
        // every My URLs render needs these; individual tests override when they care
        given(shortUrlService.getUserStats(any())).willReturn(new UserUrlStats(0L, 0L, 0L));
    }

    @Test
    void homeRenders() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>URL Shortener Service</title>")))
                // version-less @{/webjars/...} in the template, resolved by webjars-locator-lite
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.3/css/bootstrap.min.css")))
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js")))
                .andExpect(content().string(containsString("built by Darshan Gohil")))
                .andExpect(content().string(containsString("Create a Short URL")))
                .andExpect(content().string(containsString("nav-link active")));
    }

    @Test
    void homeRendersRowsFromThePagedResult() throws Exception {
        // guards the record-property reads the templates do (${url.shortKey}, ${page.data})
        given(shortUrlService.findAllPublicShortUrls(anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(1L, "aB3xZ9", false, new UserDto(1L, "Admin User")),
                TestFixtures.dto(2L, "k9M2pQ", false, null))));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://localhost:8080/s/aB3xZ9")))
                .andExpect(content().string(containsString("Admin User")))
                // a URL with no creator is shown as a guest link
                .andExpect(content().string(containsString("Guest")))
                .andExpect(content().string(containsString("7")))
                // #temporals renders an Instant in the JVM default zone
                .andExpect(content().string(containsString(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.parse("2026-01-02T03:04:05Z")))));
    }

    @Test
    void anonymousVisitorDoesNotSeeTheOwnerOnlyFormFields() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("expirationInDays"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Private (only you can access)"))))
                .andExpect(content().string(containsString(">Login</a>")));
    }

    @Test
    void signedInUserSeesTheOwnerOnlyFormFields() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/").with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("expirationInDays")))
                .andExpect(content().string(containsString("Private (only you can access)")))
                .andExpect(content().string(containsString("Signed in as")))
                .andExpect(content().string(containsString("My URLs")));
    }

    @Test
    void theCreateFormCarriesACsrfToken() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void postWithoutACsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/short-urls").param("originalUrl", "https://example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pagerRendersWhenThereIsMoreThanOnePage() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.pageOneOf(List.of(
                        TestFixtures.dto(1L, "aB3xZ9", false, null)), 3));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pagination")))
                .andExpect(content().string(containsString("?page=2")))
                .andExpect(content().string(containsString("?page=3")))
                // pager links jump back down to the table, not the top of the page
                .andExpect(content().string(containsString("?page=2#url-table")));
    }

    @Test
    void pagerCollapsesDistantPagesIntoAGap() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.pageOneOf(List.of(
                        TestFixtures.dto(1L, "aB3xZ9", false, null)), 40));
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("?page=3")))
                .andExpect(content().string(containsString("&hellip;")))
                .andExpect(content().string(containsString("?page=40")))
                .andExpect(content().string(not(containsString("?page=20\""))))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    @Test
    void pageParameterIsPassedThroughToTheService() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/").param("page", "2")).andExpect(status().isOk());
        verify(shortUrlService).findAllPublicShortUrls(2);
    }

    @Test
    void nonNumericPageRedirectsToTheFirstPage() throws Exception {
        mockMvc.perform(get("/").param("page", "abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void zeroOrNegativePageRedirectsToTheFirstPage() throws Exception {
        for (String page : List.of("0", "-5")) {
            mockMvc.perform(get("/").param("page", page))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }
        verify(shortUrlService, never()).findAllPublicShortUrls(anyInt());
    }

    @Test
    void pageBeyondTheLastRedirectsToTheLastPage() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(new PagedResult<>(List.of(), 25, 999, 3, false, true, false, true));
        mockMvc.perform(get("/").param("page", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?page=3"));
    }

    @Test
    void aboutRendersThroughLayout() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>About</title>")))
                .andExpect(content().string(containsString("navbar-brand")))
                .andExpect(content().string(containsString("About URL Shortener Service Page")));
    }

    @Test
    void alertFragmentRenders() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(get("/").flashAttr("successMessage", "Yay it worked"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-success")))
                .andExpect(content().string(containsString("Yay it worked")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("alert-danger"))));
    }

    @Test
    void invalidSubmitRedisplaysIndexWithFieldError() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(post("/short-urls").with(csrf()).param("originalUrl", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Original URL is required")));
    }

    @Test
    void shortKeyRedirectsToOriginalUrl() throws Exception {
        given(shortUrlService.accessOriginalUrl("abc123", null))
                .willReturn(Optional.of("https://example.com/landing"));
        mockMvc.perform(get("/s/abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://example.com/landing"));
    }

    @Test
    void theSignedInViewerIsPassedToTheResolver() throws Exception {
        given(shortUrlService.accessOriginalUrl("abc123", 2L))
                .willReturn(Optional.of("https://example.com/private"));
        mockMvc.perform(get("/s/abc123")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://example.com/private"));
        verify(shortUrlService).accessOriginalUrl("abc123", 2L);
    }

    @Test
    void unknownOrExpiredShortKeyRendersNotFoundPage() throws Exception {
        given(shortUrlService.accessOriginalUrl("nope00", null)).willReturn(Optional.empty());
        mockMvc.perform(get("/s/nope00"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("<title>Page Not Found</title>")))
                // rendered through the shared layout
                .andExpect(content().string(containsString("navbar-brand")))
                .andExpect(content().string(containsString("does not exist or has expired")));
    }

    @Test
    void unmappedUrlRendersNotFoundPageWithItsOwnTitle() throws Exception {
        // the layout has no "title" model attribute on this path -- the title must come
        // from error/404.html itself
        mockMvc.perform(get("/definitely-not-a-route")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("<title>Page Not Found</title>")));
    }

    @Test
    void unmappedUrlSendsAnAnonymousVisitorToLogin() throws Exception {
        // anyRequest().authenticated() is the catch-all, so a path nobody mapped is
        // treated as protected rather than silently public
        mockMvc.perform(get("/definitely-not-a-route"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unexpectedFailureRendersServerErrorPage() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willThrow(new IllegalStateException("boom"));
        mockMvc.perform(get("/"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                .andExpect(content().string(containsString("<title>Something Went Wrong</title>")));
    }

    @Test
    void nonHttpUrlIsRejected() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        mockMvc.perform(post("/short-urls").with(csrf()).param("originalUrl", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Enter a valid http(s) URL")));
    }

    @Test
    void unreachableUrlShowsFieldErrorInsteadOfCrashing() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt()))
                .willReturn(TestFixtures.onePage(List.of()));
        given(shortUrlService.createShortUrl(any()))
                .willThrow(new InvalidUrlException("Could not reach URL: https://example.invalid"));
        mockMvc.perform(post("/short-urls").with(csrf()).param("originalUrl", "https://example.invalid"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // the apostrophe renders HTML-escaped as &#39;, so assert around it
                .andExpect(content().string(containsString("reach that URL")));
    }

    @Test
    void validSubmitRedirects() throws Exception {
        given(shortUrlService.createShortUrl(any())).willReturn(new ShortUrlDto(
                1L, "abc123", "https://example.com", false, null, null, 0L, Instant.now()));
        mockMvc.perform(post("/short-urls").with(csrf()).param("originalUrl", "https://example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage",
                        containsString("http://localhost:8080/s/abc123")));
    }

    @Test
    void anonymousSubmitCarriesNoUserId() throws Exception {
        given(shortUrlService.createShortUrl(any())).willReturn(new ShortUrlDto(
                1L, "abc123", "https://example.com", false, null, null, 0L, Instant.now()));
        mockMvc.perform(post("/short-urls").with(csrf())
                        .param("originalUrl", "https://example.com")
                        .param("isPrivate", "true"))
                .andExpect(status().is3xxRedirection());

        var captor = ArgumentCaptor.forClass(CreateShortUrlCmd.class);
        verify(shortUrlService).createShortUrl(captor.capture());
        assertThat(captor.getValue().userId()).isNull();
    }

    @Test
    void signedInSubmitCarriesTheUserId() throws Exception {
        given(shortUrlService.createShortUrl(any())).willReturn(new ShortUrlDto(
                1L, "abc123", "https://example.com", true, null, null, 0L, Instant.now()));
        mockMvc.perform(post("/short-urls").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("originalUrl", "https://example.com")
                        .param("isPrivate", "true")
                        .param("expirationInDays", "7"))
                .andExpect(status().is3xxRedirection());

        var captor = ArgumentCaptor.forClass(CreateShortUrlCmd.class);
        verify(shortUrlService).createShortUrl(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(2L);
        assertThat(captor.getValue().isPrivate()).isTrue();
        assertThat(captor.getValue().expirationInDays()).isEqualTo(7);
    }

    @Test
    void myUrlsRequiresSignIn() throws Exception {
        mockMvc.perform(get("/my-urls"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void myUrlsListsOnlyTheSignedInUsersLinks() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(9L, "mine01", true, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>My URLs</title>")))
                .andExpect(content().string(containsString("http://localhost:8080/s/mine01")))
                .andExpect(content().string(containsString("Private")))
                .andExpect(content().string(containsString("Delete Selected")));
        verify(shortUrlService).findUrlsByUser(2L, 1);
    }

    @Test
    void myUrlsShowsTheUsersStats() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), anyInt())).willReturn(TestFixtures.onePage(List.of()));
        given(shortUrlService.getUserStats(2L)).willReturn(new UserUrlStats(12L, 345L, 9L));

        mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">12<")))
                .andExpect(content().string(containsString(">345<")))
                .andExpect(content().string(containsString(">9<")))
                // expired is derived: 12 links - 9 active
                .andExpect(content().string(containsString(">3<")))
                .andExpect(content().string(containsString("Total clicks")));
    }

    @Test
    void anAdminCanUseMyUrlsThroughTheRoleHierarchy() throws Exception {
        // /my-urls requires ROLE_USER; an admin only has ROLE_ADMIN, which implies it
        given(shortUrlService.findUrlsByUser(eq(1L), anyInt())).willReturn(TestFixtures.onePage(List.of()));

        mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>My URLs</title>")));
    }

    @Test
    void deletingPassesTheIdsToTheService() throws Exception {
        mockMvc.perform(post("/delete-urls").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("ids", "1", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-urls"));
        verify(shortUrlService).deleteShortUrls(List.of(1L, 2L));
    }

    @Test
    void deletingRequiresSignIn() throws Exception {
        mockMvc.perform(post("/delete-urls").with(csrf()).param("ids", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
