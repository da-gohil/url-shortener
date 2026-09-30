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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may delete or edit a link, end to end through the real controllers, the real
 * {@code @PreAuthorize} proxy and the database, which the web-slice tests (with a mocked service) cannot show. Each test
 * creates its own rows and rolls them back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnershipIntegrationTest {

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

    // --- delete ----------------------------------------------------------------------

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

    // --- edit ------------------------------------------------------------------------

    @Test
    void anotherUserCannotOpenTheEditPage() throws Exception {
        mockMvc.perform(get("/my-urls/" + ownersUrl.getId() + "/edit")
                        .with(user(new SecurityUser(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherUserCannotSaveAnEditAndNothingChanges() throws Exception {
        mockMvc.perform(post("/my-urls/" + ownersUrl.getId() + "/edit").with(csrf())
                        .with(user(new SecurityUser(stranger)))
                        .param("isPrivate", "true").param("expiry", "never"))
                .andExpect(status().isForbidden());

        assertThat(shortUrlRepository.findById(ownersUrl.getId()).orElseThrow().getIsPrivate()).isFalse();
    }

    @Test
    void theOwnersEditIsSaved() throws Exception {
        mockMvc.perform(post("/my-urls/" + ownersUrl.getId() + "/edit").with(csrf())
                        .with(user(new SecurityUser(owner)))
                        .param("isPrivate", "true").param("expiry", "days").param("expirationInDays", "5"))
                .andExpect(status().is3xxRedirection());

        ShortUrl saved = shortUrlRepository.findById(ownersUrl.getId()).orElseThrow();
        assertThat(saved.getIsPrivate()).isTrue();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(4, ChronoUnit.DAYS));
    }
}
