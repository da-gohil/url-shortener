package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.entities.AuditEvent;
import com.darshangohil.urlshortener.domain.models.AuditAction;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.repository.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Records who changed which link or account. Callers are the services that make the
 * change; the entry joins their transaction, so a change that rolls back leaves no
 * entry and an entry never exists without its change.
 */
@Service
@Transactional(readOnly = true)
public class AuditLog {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository auditEventRepository;
    private final Clock clock = Clock.systemUTC();

    public AuditLog(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /** Must run inside the caller's transaction; there is no sense in logging on its own. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, Long targetId, String summary) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityUser actor = authentication == null ? null : SecurityUser.from(authentication.getPrincipal());
        auditEventRepository.save(new AuditEvent(
                Instant.now(clock),
                actor == null ? null : actor.getId(),
                actor == null ? "anonymous" : actor.getUsername(),
                action, targetId, summary));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PagedResult<AuditEvent> findEvents(int pageNo, int pageSize) {
        var page = auditEventRepository.findAll(
                PageRequest.of(Math.max(pageNo, 1) - 1, pageSize, NEWEST_FIRST));
        return PagedResult.from(page, event -> event);
    }
}
