package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.entities.AuditEvent;
import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.AuditAction;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.repository.AuditEventRepository;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import com.darshangohil.urlshortener.domain.services.AuditLog;
import com.darshangohil.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit entries written through the real stack into the real table, then rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ShortUrlRepository shortUrlRepository;
    @Autowired AuditEventRepository auditEventRepository;
    @Autowired AuditLog auditLog;

    private User admin;
    private User owner;
    private User stranger;
    private ShortUrl link;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(TestFixtures.user(null, "Audit Test Admin", Role.ROLE_ADMIN));
        owner = userRepository.save(TestFixtures.user(null, "Audit Test Owner", Role.ROLE_USER));
        stranger = userRepository.save(TestFixtures.user(null, "Audit Test Stranger", Role.ROLE_USER));
        link = shortUrlRepository.save(TestFixtures.shortUrl(null, "audit1", false, owner));
    }

    @Test
    void anAdminActionIsRecordedWithTheActor() throws Exception {
        mockMvc.perform(post("/admin/links/" + link.getId() + "/disable").with(csrf())
                        .with(user(new SecurityUser(admin))))
                .andExpect(status().is3xxRedirection());

        AuditEvent event = only(entriesFor(link.getId()));
        assertThat(event.getAction()).isEqualTo(AuditAction.LINK_DISABLED);
        assertThat(event.getTargetType()).isEqualTo("LINK");
        assertThat(event.getActorId()).isEqualTo(admin.getId());
        assertThat(event.getActorEmail()).isEqualTo("audit.test.admin@example.com");
        assertThat(event.getSummary()).isEqualTo("audit1 → https://example.com/audit1");
    }

    @Test
    void anOwnersOwnEditIsRecordedToo() throws Exception {
        mockMvc.perform(post("/my-urls/" + link.getId() + "/edit").with(csrf())
                        .with(user(new SecurityUser(owner)))
                        .param("isPrivate", "true").param("expiry", "keep"))
                .andExpect(status().is3xxRedirection());

        AuditEvent event = only(entriesFor(link.getId()));
        assertThat(event.getAction()).isEqualTo(AuditAction.LINK_EDITED);
        assertThat(event.getActorEmail()).isEqualTo("audit.test.owner@example.com");
        assertThat(event.getSummary()).isEqualTo("audit1: public → private");
    }

    @Test
    void aRefusedChangeLeavesNoEntry() throws Exception {
        mockMvc.perform(post("/delete-urls").with(csrf()).with(user(new SecurityUser(stranger)))
                        .param("ids", link.getId().toString()))
                .andExpect(status().isForbidden());

        assertThat(entriesFor(link.getId())).isEmpty();
    }

    @Test
    void anEntryCannotBeWrittenOutsideTheChangesTransaction() {
        TestTransaction.end();   // leave the test's transaction

        assertThatThrownBy(() -> auditLog.record(AuditAction.LINK_EDITED, 1L, "orphan"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private List<AuditEvent> entriesFor(Long targetId) {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getTargetId().equals(targetId) && "LINK".equals(event.getTargetType()))
                .toList();
    }

    private static AuditEvent only(List<AuditEvent> events) {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }
}
