package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through the real controller, the real {@code @PreAuthorize} proxy and the
 * database, which the web-slice tests (with a mocked service) cannot show. Each test
 * creates its own rows and rolls them back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeleteAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ShortUrlRepository shortUrlRepository;

    private User owner;
    private User stranger;
    private User admin;
    private ShortUrl ownersUrl;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user(null, "Authz Test Owner", Role.ROLE_USER));
        stranger = userRepository.save(TestFixtures.user(null, "Authz Test Stranger", Role.ROLE_USER));
        admin = userRepository.save(TestFixtures.user(null, "Authz Test Admin", Role.ROLE_ADMIN));
        ownersUrl = shortUrlRepository.save(TestFixtures.shortUrl(null, "authz1", false, owner));
    }

    @Test
    void anotherUserGetsA403AndTheUrlSurvives() throws Exception {
        mockMvc.perform(post("/delete-urls").with(csrf()).with(user(new SecurityUser(stranger)))
                        .param("ids", ownersUrl.getId().toString()))
                .andExpect(status().isForbidden());

        assertThat(shortUrlRepository.existsById(ownersUrl.getId())).isTrue();
    }

    @Test
    void theOwnerCanDeleteIt() throws Exception {
        mockMvc.perform(post("/delete-urls").with(csrf()).with(user(new SecurityUser(owner)))
                        .param("ids", ownersUrl.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-urls"));

        assertThat(shortUrlRepository.existsById(ownersUrl.getId())).isFalse();
    }

    @Test
    void anAdminCanDeleteItFromMyUrlsToo() throws Exception {
        // the user-facing endpoint, reached through the role hierarchy
        mockMvc.perform(post("/delete-urls").with(csrf()).with(user(new SecurityUser(admin)))
                        .param("ids", ownersUrl.getId().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(shortUrlRepository.existsById(ownersUrl.getId())).isFalse();
    }
}
