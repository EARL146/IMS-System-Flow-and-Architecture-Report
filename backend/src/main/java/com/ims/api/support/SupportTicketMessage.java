package com.ims.api.support;

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

import java.time.Instant;

@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false)
    private AppUser author;

    @Column(name = "author_role", nullable = false, length = 32)
    private String authorRole;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupportTicketMessage() {
    }

    public SupportTicketMessage(AppUser author, String body, Instant createdAt) {
        this.author = author;
        this.authorRole = author.getRoles().stream()
                .map(com.ims.api.auth.Role::getCode)
                .findFirst()
                .orElse("USER");
        this.body = body;
        this.createdAt = createdAt;
    }

    void setTicket(SupportTicket ticket) {
        this.ticket = ticket;
    }

    public Long getId() { return id; }
    public SupportTicket getTicket() { return ticket; }
    public AppUser getAuthor() { return author; }
    public String getAuthorRole() { return authorRole; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
