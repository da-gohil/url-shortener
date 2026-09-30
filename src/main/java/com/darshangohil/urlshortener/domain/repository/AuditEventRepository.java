package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
