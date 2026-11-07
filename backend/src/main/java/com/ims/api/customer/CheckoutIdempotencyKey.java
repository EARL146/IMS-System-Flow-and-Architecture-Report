package com.ims.api.customer;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "checkout_idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uq_checkout_user_key", columnNames = {"user_id", "idempotency_key"})
})
public class CheckoutIdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_id", unique = true)
    private CustomerOrder customerOrder;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected CheckoutIdempotencyKey() {
    }

    public CheckoutIdempotencyKey(AppUser user, String idempotencyKey, String requestHash) {
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void setCustomerOrder(CustomerOrder customerOrder) { this.customerOrder = customerOrder; }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public CustomerOrder getCustomerOrder() { return customerOrder; }
}
