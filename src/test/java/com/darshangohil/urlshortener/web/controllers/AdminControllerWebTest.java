package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.RateLimitProperties;
import com.darshangohil.urlshortener.web.security.LoginThrottle;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.models.AdminOverview;
import com.darshangohil.urlshortener.domain.models.OwnerFilter;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.models.UserSummary;
import com.darshangohil.urlshortener.domain.exception.SelfModificationException;
import com.darshangohil.urlshortener.domain.services.AdminOverviewService;
import com.darshangohil.urlshortener.domain.services.AuditLog;
import com.darshangohil.urlshortener.domain.entities.AuditEvent;
import com.darshangohil.urlshortener.domain.models.AuditAction;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.support.TestFixtures;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@EnableConfigurationProperties({ApplicationProperties.class, RateLimitProperties.class})
@Import({SecurityConfig.class, MethodSecurityConfig.class, SecurityUtils.class, LoginThrottle.class})
@TestPropertySource(properties = "app.baseUrl=http://localhost:8080")
class AdminControllerWebTest {

    private static final SecurityUser ADMIN = TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN);
    private static final SecurityUser JOHN = TestFixtures.principal(2L, "John Doe", Role.ROLE_USER);

    @Autowired MockMvc mockMvc;
    @MockitoBean ShortUrlService shortUrlService;
    @MockitoBean UserService userService;
    @MockitoBean AdminOverviewService adminOverviewService;
    @MockitoBean AuditLog auditLog;

    // --- access ----------------------------------------------------------------------

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/links"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/links").with(user(JOHN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotReachTheAdminDelete() throws Exception {
        mockMvc.perform(post("/admin/delete-urls").with(csrf()).with(user(JOHN)).param("ids", "4"))
                .andExpect(status().isForbidden());
    }

    // --- overview --------------------------------------------------------------------

    @Test
    void theOverviewShowsTotalsTheChartAndTopLinks() throws Exception {
        var days = new java.util.ArrayList<AdminOverview.DailyCount>();
        for (int i = 0; i < 14; i++) {
            days.add(new AdminOverview.DailyCount(java.time.LocalDate.of(2026, 9, 17).plusDays(i), i == 5 ? 8 : 1));
        }
        given(adminOverviewService.getOverview()).willReturn(new AdminOverview(1234,
                new UserUrlStats(50L, 12345L, 40L, 3L), days,
                List.of(TestFixtures.dto(1L, "aB3xZ9", false, null))));

        String html = mockMvc.perform(get("/admin/dashboard").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("<title>Admin Dashboard · URL Shortener</title>")
                .contains(">1,234<")                  // users, comma-grouped
                .contains(">12,345<")                 // total clicks
                .contains("Last 14 days · 21 links")
                // one column per day; the busiest (8) is full height and the only labelled bar
                .containsPattern("(?s)(class=\"day-chart__col\".*?){14}")
                .contains("height:100%")
                .contains("height:12%")
                .contains("class=\"day-chart__peak\">8</span>")
                .contains("Tue 22 Sep: 8 links")
                .contains("Show as a table")
                .contains(">aB3xZ9</a>");
    }

    // --- links tab -------------------------------------------------------------------

    @Test
    void adminSeesEveryUsersLinksWithEditAndDeleteControls() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(1L, "aB3xZ9", false, new UserDto(1L, "Admin User")),
                TestFixtures.dto(4L, "p0L8kJ", true, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/admin/links").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Admin · Links · URL Shortener</title>")))
                .andExpect(content().string(containsString("John Doe")))
                .andExpect(content().string(containsString("Delete Selected")))
                // edit links come back to the admin tab, not the admin's own My URLs
                .andExpect(content().string(containsString("href=\"/my-urls/4/edit?from=admin\"")));
    }

    @Test
    void filtersAndOwnerArePassedToTheService() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));
        given(userService.findUser(2L)).willReturn(Optional.of(new UserDto(2L, "John Doe")));

        mockMvc.perform(get("/admin/links").with(user(ADMIN))
                        .param("owner", "2").param("status", "expired").param("q", "docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Showing links by <strong>John Doe</strong>")))
                // the owner survives a resubmit of the filter form
                .andExpect(content().string(containsString("name=\"owner\" value=\"2\"")))
                .andExpect(content().string(containsString("No links match these filters.")));

        verify(shortUrlService).findAllShortUrls(
                ShortUrlFilter.parse("docs", null, "expired", null), OwnerFilter.user(2L), 1);
    }

    @Test
    void guestLinksCanBeListedOnTheirOwn() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));

        mockMvc.perform(get("/admin/links").with(user(ADMIN)).param("owner", "guest"))
                .andExpect(content().string(containsString("Showing links by <strong>guests</strong>")));

        verify(shortUrlService).findAllShortUrls(ShortUrlFilter.NONE, OwnerFilter.GUESTS, 1);
    }

    @Test
    void pagerAndRedirectsKeepTheOwner() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt()))
                .willReturn(new PagedResult<>(List.of(), 25, 9, 3, false, true, false, true));

        mockMvc.perform(get("/admin/links").with(user(ADMIN)).param("owner", "guest").param("page", "9"))
                .andExpect(redirectedUrl("/admin/links?owner=guest&page=3"));
    }

    @Test
    void rowsOfferDisableAndReturnToTheSameListing() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(4L, "p0L8kJ", false, new UserDto(2L, "John Doe")),
                new ShortUrlDto(5L, "off001", "https://example.com", false, null, null, 0L,
                        java.time.Instant.now(), true))));

        String html = mockMvc.perform(get("/admin/links").with(user(ADMIN)).param("status", "all").param("sort", "clicks"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("action=\"/admin/links/4/disable\"")
                .contains("action=\"/admin/links/5/enable\"")
                .contains("name=\"returnTo\" value=\"/admin/links?sort=clicks&amp;page=1\"")
                .contains(">Disabled</span>");
    }

    @Test
    void disablingRedirectsBackWithTheListingIntact() throws Exception {
        given(shortUrlService.setDisabled(4L, true)).willReturn(TestFixtures.dto(4L, "p0L8kJ", false, null));

        mockMvc.perform(post("/admin/links/4/disable").with(csrf()).with(user(ADMIN))
                        .param("returnTo", "/admin/links?owner=2&page=3"))
                .andExpect(redirectedUrl("/admin/links?owner=2&page=3"))
                .andExpect(flash().attribute("successMessage", "Disabled p0L8kJ: it no longer redirects"));
        verify(shortUrlService).setDisabled(4L, true);
    }

    @Test
    void anOffSiteReturnToIsIgnored() throws Exception {
        given(shortUrlService.setDisabled(4L, false)).willReturn(TestFixtures.dto(4L, "p0L8kJ", false, null));

        for (String evil : List.of("https://evil.example", "//evil.example", "/admin/linksevil", "/my-urls")) {
            mockMvc.perform(post("/admin/links/4/enable").with(csrf()).with(user(ADMIN)).param("returnTo", evil))
                    .andExpect(redirectedUrl("/admin/links"));
        }
    }

    @Test
    void ordinaryUserCannotDisableALink() throws Exception {
        mockMvc.perform(post("/admin/links/4/disable").with(csrf()).with(user(JOHN)))
                .andExpect(status().isForbidden());
    }

    // --- users tab -------------------------------------------------------------------

    private static UserSummary summary(Long id, String name, Role role, boolean enabled, long links) {
        return new UserSummary(id, name, name.toLowerCase().replace(' ', '.') + "@example.com",
                role, enabled, java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"), links);
    }

    @Test
    void usersTabListsAccountsButOffersNoActionsOnYourself() throws Exception {
        given(userService.findUsers(any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                summary(1L, "Admin User", Role.ROLE_ADMIN, true, 1),
                summary(2L, "John Doe", Role.ROLE_USER, false, 7))));

        String html = mockMvc.perform(get("/admin/users").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("<title>Admin · Users · URL Shortener</title>")
                .contains(">You</span>")
                // John: link count goes to his links, and he can be promoted or re-enabled
                .contains("href=\"/admin/links?owner=2\"")
                .contains("action=\"/admin/users/2/role\"")
                .contains("action=\"/admin/users/2/enable\"")
                // the signed-in admin's own row has no forms
                .doesNotContain("/admin/users/1/role")
                .doesNotContain("/admin/users/1/disable");
    }

    @Test
    void theUserSearchIsPassedAlong() throws Exception {
        given(userService.findUsers(any(), anyInt())).willReturn(TestFixtures.onePage(List.of()));

        mockMvc.perform(get("/admin/users").with(user(ADMIN)).param("q", " john "))
                .andExpect(content().string(containsString("No users match.")));
        verify(userService).findUsers("john", 1);
    }

    @Test
    void promotingAUserReportsIt() throws Exception {
        given(userService.changeRole(2L, Role.ROLE_ADMIN, 1L)).willReturn(new UserDto(2L, "John Doe"));

        mockMvc.perform(post("/admin/users/2/role").with(csrf()).with(user(ADMIN))
                        .param("role", "ROLE_ADMIN").param("returnTo", "/admin/users?q=john&page=1"))
                .andExpect(redirectedUrl("/admin/users?q=john&page=1"))
                .andExpect(flash().attribute("successMessage", "John Doe is now an admin"));
    }

    @Test
    void refusingSelfModificationShowsTheReason() throws Exception {
        given(userService.setEnabled(1L, false, 1L))
                .willThrow(new SelfModificationException("You can't disable your own account. Ask another admin."));

        mockMvc.perform(post("/admin/users/1/disable").with(csrf()).with(user(ADMIN)))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "You can't disable your own account. Ask another admin."));
    }

    @Test
    void ordinaryUserCannotManageUsers() throws Exception {
        mockMvc.perform(get("/admin/users").with(user(JOHN))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/3/disable").with(csrf()).with(user(JOHN)))
                .andExpect(status().isForbidden());
    }

    // --- audit log -------------------------------------------------------------------

    @Test
    void theAuditLogListsEntriesNewestFirst() throws Exception {
        given(auditLog.findEvents(1, 20)).willReturn(TestFixtures.onePage(List.of(
                new AuditEvent(java.time.Instant.parse("2026-09-30T12:00:00Z"), 1L, "admin@example.com",
                        AuditAction.USER_DISABLED, 2L, "John Doe <john.doe@example.com>"),
                new AuditEvent(java.time.Instant.parse("2026-09-30T11:00:00Z"), 2L, "john.doe@example.com",
                        AuditAction.LINK_EDITED, 9L, "mine01: public → private"))));

        mockMvc.perform(get("/admin/audit").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Admin · Audit log · URL Shortener</title>")))
                .andExpect(content().string(containsString("Disabled account")))
                .andExpect(content().string(containsString("John Doe &lt;john.doe@example.com&gt;")))
                .andExpect(content().string(containsString("href=\"/admin/links?owner=2\"")))
                .andExpect(content().string(containsString("mine01: public → private")));
    }

    @Test
    void ordinaryUserCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/admin/audit").with(user(JOHN))).andExpect(status().isForbidden());
    }

    @Test
    void adminDeleteReturnsToTheLinksTab() throws Exception {
        mockMvc.perform(post("/admin/delete-urls").with(csrf()).with(user(ADMIN)).param("ids", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/links"));
        verify(shortUrlService).deleteShortUrls(List.of(4L));
    }
}
