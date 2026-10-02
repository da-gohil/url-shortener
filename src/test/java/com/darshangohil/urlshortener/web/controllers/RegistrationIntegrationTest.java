package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.entities.AuditEvent;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.AuditAction;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.repository.AuditEventRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration against the real database and the real security chain: the account
 * that gets stored, the sign-in that follows, and that the new session behaves like
 * one from the login form. Rows are created per test and rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationIntegrationTest {

    // the sign-up limiter lives as long as the application context, which other test
    // classes share, so every request here comes from an address of its own
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AuditEventRepository auditEventRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registeringStoresAnOrdinaryAccountAndSignsItIn() throws Exception {
        MockHttpSession session = register("Reg Test Newcomer", "Reg.Newcomer@Example.com");

        User saved = userRepository.findByEmailIgnoreCase("reg.newcomer@example.com").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("reg.newcomer@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(passwordEncoder.matches("s3cretpassword", saved.getPassword())).isTrue();

        mockMvc.perform(get("/my-urls").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reg Test Newcomer")));
    }

    @Test
    void theSignInGetsAFreshSessionId() throws Exception {
        // a visitor already has a session by the time they submit: it holds their CSRF token
        var visitor = (MockHttpSession) mockMvc.perform(get("/register"))
                .andReturn().getRequest().getSession();
        String before = visitor.getId();

        MockHttpSession after = register("Reg Test Fixation", "reg.fixation@example.com", visitor);

        assertThat(after.getId()).isNotEqualTo(before);
    }

    @Test
    void theNewSessionCanBeEndedByAnAdminLikeAnyOther() throws Exception {
        MockHttpSession session = register("Reg Test Ended", "reg.ended@example.com");
        Long id = userRepository.findByEmailIgnoreCase("reg.ended@example.com").orElseThrow().getId();
        User admin = userRepository.save(TestFixtures.user(null, "Reg Test Admin", Role.ROLE_ADMIN));

        mockMvc.perform(post("/admin/users/" + id + "/disable").with(csrf())
                        .with(user(new SecurityUser(admin))))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/my-urls").session(session))
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void registrationIsInTheAuditLog() throws Exception {
        register("Reg Test Audited", "reg.audited@example.com");
        Long id = userRepository.findByEmailIgnoreCase("reg.audited@example.com").orElseThrow().getId();

        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> event.getAction() == AuditAction.USER_REGISTERED)
                .extracting(AuditEvent::getTargetId, AuditEvent::getSummary)
                .contains(org.assertj.core.groups.Tuple.tuple(id, "Reg Test Audited <reg.audited@example.com>"));
    }

    @Test
    void anEmailThatDiffersOnlyInCaseIsAlreadyTaken() throws Exception {
        register("Reg Test Original", "reg.original@example.com");

        mockMvc.perform(registration("Reg Test Copycat", "REG.Original@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("An account with that email already exists")));
        assertThat(userRepository.findAll())
                .filteredOn(u -> u.getEmail().equalsIgnoreCase("reg.original@example.com"))
                .hasSize(1);
    }

    private MockHttpSession register(String name, String email) throws Exception {
        return register(name, email, new MockHttpSession());
    }

    private MockHttpSession register(String name, String email, MockHttpSession session) throws Exception {
        return (MockHttpSession) mockMvc.perform(registration(name, email).session(session))
                .andExpect(redirectedUrl("/my-urls"))
                .andReturn().getRequest().getSession();
    }

    private static MockHttpServletRequestBuilder registration(String name, String email) {
        String address = "198.51.100." + NEXT_ADDRESS.getAndIncrement();
        return post("/register").with(csrf())
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .param("name", name)
                .param("email", email)
                .param("password", "s3cretpassword")
                .param("confirmPassword", "s3cretpassword");
    }
}
