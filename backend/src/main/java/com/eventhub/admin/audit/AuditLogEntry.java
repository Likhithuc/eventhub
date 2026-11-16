package com.eventhub.admin.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only record of an admin action. Never updated or deleted by the application. */
@Entity
@Table(name = "admin_audit_log")
public class AuditLogEntry {

    public enum Action {
        EVENT_APPROVED, EVENT_REJECTED, EVENT_UNPUBLISHED,
        USER_SUSPENDED, USER_REACTIVATED,
        ORGANIZER_VERIFIED, ORGANIZER_VERIFICATION_REJECTED, ORGANIZER_VERIFICATION_RESET
    }

    public enum TargetType { EVENT, USER, ORGANIZER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** Human-readable one-liner, e.g. the event title, captured at the time of the action. */
    @Column(nullable = false, length = 300)
    private String summary;

    @Column(length = 1000)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(UUID actorId, Action action, TargetType targetType, UUID targetId, String summary, String details) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary.length() > 300 ? summary.substring(0, 300) : summary;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public Action getAction() { return action; }
    public TargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
