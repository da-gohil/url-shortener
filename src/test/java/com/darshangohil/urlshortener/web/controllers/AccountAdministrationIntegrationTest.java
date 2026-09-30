package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.models.UserSummary;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account administration against the real database and the real security chain:
 * signing in through the login form, the session registry, and the user-summary query.
 * Rows are created per test and rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountAdministrationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ShortUrlRepository shortUrlRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = save(TestFixtures.user(null, "Acct Test Admin", Role.ROLE_ADMIN));
        member = save(TestFixtures.user(null, "Acct Test Member", Role.ROLE_USER));
        shortUrlRepository.save(TestFixtures.shortUrl(null, "acct01", false, member));
        shortUrlRepository.save(TestFixtures.shortUrl(null, "acct02", false, member));
    }

    // --- the Users tab query ---------------------------------------------------------

    @Test
    void summariesCountEachUsersLinks() {
        List<UserSummary> rows = userRepository
                .findUserSummaries("acct test", PageRequest.of(0, 50)).getContent();

        assertThat(rows).extracting(UserSummary::name, UserSummary::linkCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Acct Test Admin", 0L),
                        org.assertj.core.groups.Tuple.tuple("Acct Test Member", 2L));
    }

    @Test
    void anEmptySearchListsEveryone() {
        long everyone = userRepository.count();

        assertThat(userRepository.findUserSummaries("", PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(everyone);
    }

    // --- signing in and sessions -----------------------------------------------------

    @Test
    void aDisabledAccountCannotSignIn() throws Exception {
        member.setEnabled(false);

        mockMvc.perform(formLogin("/login").user("email", member.getEmail()).password("password", "pw-123456"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void disablingAnAccountEndsItsLiveSession() throws Exception {
        MockHttpSession memberSession = signIn(member);
        mockMvc.perform(get("/my-urls").session(memberSession)).andExpect(status().isOk());

        mockMvc.perform(post("/admin/users/" + member.getId() + "/disable").with(csrf())
                        .with(user(new SecurityUser(admin))))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/my-urls").session(memberSession))
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void aRoleChangeEndsTheSessionSoTheNewRoleApplies() throws Exception {
        MockHttpSession memberSession = signIn(member);

        mockMvc.perform(post("/admin/users/" + member.getId() + "/role").with(csrf())
                        .with(user(new SecurityUser(admin))).param("role", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/my-urls").session(memberSession))
                .andExpect(redirectedUrl("/login?expired"));
        // signing in again picks up the new role
        mockMvc.perform(get("/admin/links").session(signIn(member))).andExpect(status().isOk());
    }

    private MockHttpSession signIn(User user) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login")
                        .user("email", user.getEmail()).password("password", "pw-123456"))
                .andExpect(redirectedUrl("/my-urls"))
                .andReturn().getRequest().getSession();
    }

    private User save(User user) {
        user.setPassword(passwordEncoder.encode("pw-123456"));
        return userRepository.save(user);
    }
}
