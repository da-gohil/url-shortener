package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, SecurityUtils.class})
@TestPropertySource(properties = "app.baseUrl=http://localhost:8080")
class AdminControllerWebTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ShortUrlService shortUrlService;

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesEveryUsersUrls() throws Exception {
        given(shortUrlService.findAllShortUrls(anyInt())).willReturn(TestFixtures.onePage(List.of(
                TestFixtures.dto(1L, "aB3xZ9", false, new UserDto(1L, "Admin User")),
                TestFixtures.dto(4L, "p0L8kJ", true, new UserDto(2L, "John Doe")))));

        mockMvc.perform(get("/admin/dashboard")
                        .with(user(TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Admin Dashboard</title>")))
                .andExpect(content().string(containsString("John Doe")))
                .andExpect(content().string(containsString("Admin User")))
                .andExpect(content().string(containsString("Delete Selected")));
    }

    @Test
    void adminDeletePassesTheIdsToTheService() throws Exception {
        mockMvc.perform(post("/admin/delete-urls").with(csrf())
                        .with(user(TestFixtures.principal(1L, "Admin User", Role.ROLE_ADMIN)))
                        .param("ids", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
        verify(shortUrlService).deleteShortUrls(List.of(4L));
    }

    @Test
    void ordinaryUserCannotReachTheAdminDelete() throws Exception {
        mockMvc.perform(post("/admin/delete-urls").with(csrf())
                        .with(user(TestFixtures.principal(2L, "John Doe", Role.ROLE_USER)))
                        .param("ids", "4"))
                .andExpect(status().isForbidden());
    }
}
