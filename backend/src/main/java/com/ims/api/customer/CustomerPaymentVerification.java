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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_payment_verifications", uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_payment_order", columnNames = "customer_order_id"),
        @UniqueConstraint(name = "uq_customer_payment_idempotency", columnNames = "idempotency_key")
})
public class CustomerPaymentVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_order_id", nullable = false, unique = true)
    private CustomerOrder customerOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verified_by_user_id", nullable = false)
    private AppUser verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String notes;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36,
            columnDefinition = "char(36)")
    private String idempotencyKey;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "verified_at", nullable = false, insertable = false, updatable = false)
    private Instant verifiedAt;

    protected CustomerPaymentVerification() {
    }

    public CustomerPaymentVerification(CustomerOrder customerOrder, AppUser verifiedBy,
                                       PaymentMethod paymentMethod, String currencyCode, BigDecimal amount,
                                       String reference, String notes, String idempotencyKey) {
        this.customerOrder = customerOrder;
        this.verifiedBy = verifiedBy;
        this.paymentMethod = paymentMethod;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.reference = reference;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    public AppUser getVerifiedBy() { return verifiedBy; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public String getNotes() { return notes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
