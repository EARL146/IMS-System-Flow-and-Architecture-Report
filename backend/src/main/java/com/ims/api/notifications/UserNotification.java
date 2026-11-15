package com.ims.api.notifications;

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
@Table(name = "user_notifications")
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private AppUser recipient;

    @Column(name = "notification_type", nullable = false, length = 48)
    private String notificationType;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "reference_type", length = 48)
    private String referenceType;

    @Column(name = "reference_id", length = 80)
    private String referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected UserNotification() {
    }

    public UserNotification(
            AppUser recipient,
            String notificationType,
            String title,
            String message,
            String referenceType,
            String referenceId
    ) {
        this.recipient = recipient;
        this.notificationType = notificationType;
        this.title = title.length() > 180 ? title.substring(0, 180) : title;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.read = false;
    }

    public void markRead(Instant at) {
        if (!this.read) {
            this.read = true;
            this.readAt = at;
        }
    }

    public Long getId() {
        return id;
    }

    public AppUser getRecipient() {
        return recipient;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
