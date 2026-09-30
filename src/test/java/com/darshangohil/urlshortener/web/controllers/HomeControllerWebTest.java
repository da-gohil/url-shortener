package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlNotFoundException;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd;
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
        given(shortUrlService.getUserStats(any())).willReturn(new UserUrlStats(0L, 0L, 0L, 0L));
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
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(9L, "mine01", true, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>My URLs</title>")))
                .andExpect(content().string(containsString("http://localhost:8080/s/mine01")))
                .andExpect(content().string(containsString("Private")))
                .andExpect(content().string(containsString("Delete Selected")));
        verify(shortUrlService).findUrlsByUser(2L, ShortUrlFilter.NONE, 1);
    }

    @Test
    void myUrlsShowsTheUsersStats() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));
        given(shortUrlService.getUserStats(2L)).willReturn(new UserUrlStats(12L, 345L, 9L, 0L));

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
    void myUrlsPassesTheFilterToTheService() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));

        mockMvc.perform(get("/my-urls")
                        .param("q", " docs ").param("visibility", "private")
                        .param("status", "EXPIRED").param("sort", "clicks")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"docs\"")))
                .andExpect(content().string(containsString("No links match these filters.")))
                .andExpect(content().string(containsString(">Clear</a>")));

        verify(shortUrlService).findUrlsByUser(2L, new ShortUrlFilter("docs",
                ShortUrlFilter.Visibility.PRIVATE, ShortUrlFilter.Status.EXPIRED,
                ShortUrlFilter.SortOrder.CLICKS), 1);
    }

    @Test
    void unknownFilterValuesFallBackToTheDefaults() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));

        mockMvc.perform(get("/my-urls").param("visibility", "sideways").param("sort", "random")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No URLs to show yet.")));

        verify(shortUrlService).findUrlsByUser(2L, ShortUrlFilter.NONE, 1);
    }

    @Test
    void pagerLinksKeepTheFilters() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.pageOneOf(List.of(
                TestFixtures.dto(9L, "mine01", false, new UserDto(2L, "John Doe"))), 3));

        mockMvc.perform(get("/my-urls").param("q", "a&b").param("status", "active")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                // the search text is URL-encoded, and page= is appended with &
                .andExpect(content().string(containsString(
                        "/my-urls?q=a%26b&amp;status=active&amp;page=2#url-table")));
    }

    @Test
    void outOfRangePagesRedirectWithTheFiltersKept() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt()))
                .willReturn(new PagedResult<>(List.of(), 25, 9, 3, false, true, false, true));

        mockMvc.perform(get("/my-urls").param("page", "9").param("sort", "oldest")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(redirectedUrl("/my-urls?sort=oldest&page=3"));
        mockMvc.perform(get("/my-urls").param("page", "0").param("sort", "oldest")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(redirectedUrl("/my-urls?sort=oldest"));
    }

    @Test
    void myUrlsRowsHaveCopySelectAllAndTheirOwnDeleteForm() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(9L, "mine01", false, new UserDto(2L, "John Doe")))));

        String html = mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("data-copy=\"http://localhost:8080/s/mine01\"")
                .contains("data-select-all")
                .contains("data-row-select")
                // the row's Delete button submits a separate one-row form...
                .contains("form=\"delete-row-9\"")
                .contains("data-confirm=\"Delete mine01?\"")
                .containsPattern("<form id=\"delete-row-9\" action=\"/delete-urls\" method=\"post\" hidden>")
                // ...which carries only that id and its own CSRF token
                .containsPattern("(?s)id=\"delete-row-9\".*?name=\"_csrf\".*?name=\"ids\" value=\"9\"");
    }

    @Test
    void expiredLinksAreBadged() throws Exception {
        var expired = new ShortUrlDto(9L, "old001", "https://example.com", false,
                Instant.now().minusSeconds(60), new UserDto(2L, "John Doe"), 0L, Instant.now());
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt()))
                .willReturn(TestFixtures.onePage(List.of(expired)));

        mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(content().string(containsString(">Expired</span>")));
    }

    @Test
    void theHomePageOffersCopyButtonsButNoDeleteControls() throws Exception {
        given(shortUrlService.findAllPublicShortUrls(anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(1L, "aB3xZ9", false, null))));

        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-copy=\"http://localhost:8080/s/aB3xZ9\"")
                .doesNotContain("data-select-all")
                .doesNotContain("delete-row-");
    }

    @Test
    void theScriptIsServedToAnonymousVisitors() throws Exception {
        // the home page loads it for everyone, so it must not sit behind the login
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-copy")));
    }

    // --- editing a link ---------------------------------------------------------------

    private static final ShortUrlDto EDITABLE = new ShortUrlDto(9L, "mine01", "https://example.com/page",
            true, null, new UserDto(2L, "John Doe"), 4L, Instant.parse("2026-01-02T03:04:05Z"));

    @Test
    void myUrlsRowsLinkToTheirEditPage() throws Exception {
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(9L, "mine01", false, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/my-urls").with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(content().string(containsString("href=\"/my-urls/9/edit\"")));
    }

    @Test
    void theEditPageShowsTheLinkAndItsCurrentSettings() throws Exception {
        given(shortUrlService.getShortUrl(9L)).willReturn(EDITABLE);

        mockMvc.perform(get("/my-urls/9/edit").with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Edit Link</title>")))
                .andExpect(content().string(containsString("http://localhost:8080/s/mine01")))
                .andExpect(content().string(containsString("https://example.com/page")))
                // currently private, so the box starts ticked; expiry defaults to "keep"
                .andExpect(content().string(containsString("checked=\"checked\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void savingAnEditUpdatesTheLinkAndReturnsToMyUrls() throws Exception {
        given(shortUrlService.updateShortUrl(eq(9L), any())).willReturn(EDITABLE);

        mockMvc.perform(post("/my-urls/9/edit").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("isPrivate", "true").param("expiry", "days").param("expirationInDays", "30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-urls"))
                .andExpect(flash().attribute("successMessage", "Updated http://localhost:8080/s/mine01"));

        verify(shortUrlService).updateShortUrl(9L,
                new UpdateShortUrlCmd(true, UpdateShortUrlCmd.Expiry.DAYS, 30));
    }

    @Test
    void anAdminEditingFromTheDashboardGoesBackThere() throws Exception {
        given(shortUrlService.getShortUrl(9L)).willReturn(EDITABLE);
        given(shortUrlService.updateShortUrl(eq(9L), any())).willReturn(EDITABLE);
        var admin = user(TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN));

        mockMvc.perform(get("/my-urls/9/edit").param("from", "admin").with(admin))
                .andExpect(content().string(containsString("Admin · Links")))
                .andExpect(content().string(containsString("name=\"from\" value=\"admin\"")));
        mockMvc.perform(post("/my-urls/9/edit").with(csrf()).with(admin)
                        .param("from", "admin").param("expiry", "keep"))
                .andExpect(redirectedUrl("/admin/links"));
    }

    @Test
    void anUnknownFromValueIsIgnoredRatherThanFollowed() throws Exception {
        given(shortUrlService.updateShortUrl(eq(9L), any())).willReturn(EDITABLE);

        mockMvc.perform(post("/my-urls/9/edit").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("from", "https://evil.example").param("expiry", "keep"))
                .andExpect(redirectedUrl("/my-urls"));
    }

    @Test
    void choosingDaysWithoutANumberRedisplaysTheForm() throws Exception {
        given(shortUrlService.getShortUrl(9L)).willReturn(EDITABLE);

        mockMvc.perform(post("/my-urls/9/edit").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("expiry", "days"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter how many days until the link expires")));
        verify(shortUrlService, never()).updateShortUrl(any(), any());
    }

    @Test
    void anOutOfRangeNumberOfDaysIsRejected() throws Exception {
        given(shortUrlService.getShortUrl(9L)).willReturn(EDITABLE);

        mockMvc.perform(post("/my-urls/9/edit").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("expiry", "days").param("expirationInDays", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter between 1 and 365 days")));
        verify(shortUrlService, never()).updateShortUrl(any(), any());
    }

    @Test
    void editingAnUnknownLinkIsNotFound() throws Exception {
        given(shortUrlService.getShortUrl(404L)).willThrow(new ShortUrlNotFoundException("gone"));

        mockMvc.perform(get("/my-urls/404/edit").with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingRequiresSignIn() throws Exception {
        mockMvc.perform(get("/my-urls/9/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ownersAreToldWhenAnAdminDisabledTheirLinks() throws Exception {
        var disabled = new ShortUrlDto(9L, "mine01", "https://example.com", false, null,
                new UserDto(2L, "John Doe"), 0L, Instant.now(), true);
        given(shortUrlService.findUrlsByUser(eq(2L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(disabled)));
        given(shortUrlService.getUserStats(2L)).willReturn(new UserUrlStats(1L, 0L, 0L, 1L));

        String html = mockMvc.perform(get("/my-urls")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("1 of your links was disabled by an admin")
                .contains(">Disabled</span>")
                // owners cannot re-enable: the toggle is admin-only
                .doesNotContain("toggle-row-");
    }

    @Test
    void anAdminCanUseMyUrlsThroughTheRoleHierarchy() throws Exception {
        // /my-urls requires ROLE_USER; an admin only has ROLE_ADMIN, which implies it
        given(shortUrlService.findUrlsByUser(eq(1L), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));

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
