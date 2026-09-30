package com.darshangohil.urlshortener.domain.entities;

import com.darshangohil.urlshortener.domain.models.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the audit log. Written once, never updated. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Null for an anonymous actor, or if the account was since removed. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /** LINK or USER; follows from the action, stored so the table reads on its own. */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "summary", nullable = false)
    private String summary;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, Long actorId, String actorEmail,
                      AuditAction action, Long targetId, String summary) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.action = action;
        this.targetType = action.targetType();
        this.targetId = targetId;
        this.summary = summary;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public AuditAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getSummary() { return summary; }
}
