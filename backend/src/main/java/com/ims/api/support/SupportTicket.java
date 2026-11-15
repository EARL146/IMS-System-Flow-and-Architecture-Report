package com.ims.api.support;

import com.ims.api.auth.AppUser;
import com.ims.api.customer.CustomerOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 40)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_id")
    private CustomerOrder customerOrder;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SupportTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupportTicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private AppUser assignedTo;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<SupportTicketMessage> messages = new ArrayList<>();

    protected SupportTicket() {
    }

    public SupportTicket(String ticketNumber, AppUser customer, CustomerOrder customerOrder,
                         String subject, String category, SupportTicketPriority priority) {
        this.ticketNumber = ticketNumber;
        this.customer = customer;
        this.customerOrder = customerOrder;
        this.subject = subject;
        this.category = category;
        this.priority = priority == null ? SupportTicketPriority.NORMAL : priority;
        this.status = SupportTicketStatus.OPEN;
    }

    public void addMessage(SupportTicketMessage message) {
        message.setTicket(this);
        this.messages.add(message);
    }

    public void updateStatus(SupportTicketStatus newStatus, AppUser actor, String notes, Instant at) {
        this.status = newStatus;
        boolean isCustomer = actor != null && actor.getRoles().stream().allMatch(r -> "CUSTOMER".equals(r.getCode()));
        if (this.assignedTo == null && actor != null && !isCustomer) {
            this.assignedTo = actor;
        }
        if (notes != null && !notes.isBlank()) {
            this.resolutionNotes = notes.trim();
        }
        if (newStatus == SupportTicketStatus.RESOLVED || newStatus == SupportTicketStatus.CLOSED) {
            this.resolvedAt = at;
        }
    }

    public Long getId() { return id; }
    public String getTicketNumber() { return ticketNumber; }
    public AppUser getCustomer() { return customer; }
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    public String getSubject() { return subject; }
    public String getCategory() { return category; }
    public SupportTicketPriority getPriority() { return priority; }
    public SupportTicketStatus getStatus() { return status; }
    public AppUser getAssignedTo() { return assignedTo; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<SupportTicketMessage> getMessages() { return messages; }
}
