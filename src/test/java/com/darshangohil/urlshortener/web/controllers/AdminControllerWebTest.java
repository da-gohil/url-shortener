package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.models.OwnerFilter;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.UserDto;
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
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, SecurityUtils.class})
@TestPropertySource(properties = "app.baseUrl=http://localhost:8080")
class AdminControllerWebTest {

    private static final SecurityUser ADMIN = TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN);
    private static final SecurityUser JOHN = TestFixtures.principal(2L, "John Doe", Role.ROLE_USER);

    @Autowired MockMvc mockMvc;
    @MockitoBean ShortUrlService shortUrlService;
    @MockitoBean UserService userService;

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

    // --- links tab -------------------------------------------------------------------

    @Test
    void adminSeesEveryUsersLinksWithEditAndDeleteControls() throws Exception {
        given(shortUrlService.findAllShortUrls(any(), any(), anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(1L, "aB3xZ9", false, new UserDto(1L, "Admin User")),
                TestFixtures.dto(4L, "p0L8kJ", true, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/admin/links").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Admin · Links</title>")))
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

    @Test
    void adminDeleteReturnsToTheLinksTab() throws Exception {
        mockMvc.perform(post("/admin/delete-urls").with(csrf()).with(user(ADMIN)).param("ids", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/links"));
        verify(shortUrlService).deleteShortUrls(List.of(4L));
    }
}
