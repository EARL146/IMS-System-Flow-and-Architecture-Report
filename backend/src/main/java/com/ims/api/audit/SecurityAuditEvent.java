package com.ims.api.audit;

import com.ims.api.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private AppUser actor;

    @Column(name = "actor_email", length = 254)
    private String actorEmail;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @Column(nullable = false, length = 500)
    private String summary;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    protected SecurityAuditEvent() {
    }

    public SecurityAuditEvent(
            String eventType,
            AppUser actor,
            String actorEmail,
            String targetType,
            String targetId,
            String summary
    ) {
        this.eventType = eventType;
        this.actor = actor;
        this.actorEmail = actor != null ? actor.getEmail() : actorEmail;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
