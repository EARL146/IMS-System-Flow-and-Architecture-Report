package com.ims.api.customer;

import com.ims.api.auth.AppUser;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "customer_order_status_history")
public class CustomerOrderStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_order_id", nullable = false)
    private CustomerOrder customerOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private CustomerOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private CustomerOrderStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private AppUser changedBy;

    @Column(length = 500)
    private String note;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    protected CustomerOrderStatusHistory() {
    }

    public CustomerOrderStatusHistory(CustomerOrder customerOrder, CustomerOrderStatus fromStatus,
                                      CustomerOrderStatus toStatus, AppUser changedBy, String note) {
        this.customerOrder = customerOrder;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
    }

    public Long getId() { return id; }
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    public CustomerOrderStatus getFromStatus() { return fromStatus; }
    public CustomerOrderStatus getToStatus() { return toStatus; }
    public AppUser getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
