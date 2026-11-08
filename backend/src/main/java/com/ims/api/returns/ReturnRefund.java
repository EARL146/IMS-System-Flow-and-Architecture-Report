package com.ims.api.returns;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "return_refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_refunds_case", columnNames = "return_case_id"),
        @UniqueConstraint(name = "uq_return_refunds_number", columnNames = "refund_number"),
        @UniqueConstraint(name = "uq_return_refunds_idempotency", columnNames = "idempotency_key")
})
public class ReturnRefund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_case_id", nullable = false, unique = true)
    private ReturnCase returnCase;

    @Column(name = "refund_number", nullable = false, length = 40)
    private String refundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 32)
    private ReturnRefundMethod refundMethod;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private AppUser recordedBy;

    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ReturnRefund() {
    }

    public ReturnRefund(ReturnCase returnCase, String refundNumber, ReturnRefundMethod refundMethod,
                        BigDecimal amount, String currencyCode, String reference, String notes,
                        AppUser recordedBy, String idempotencyKey) {
        this.returnCase = returnCase;
        this.refundNumber = refundNumber;
        this.refundMethod = refundMethod;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.reference = reference;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public String getRefundNumber() { return refundNumber; }
    public ReturnRefundMethod getRefundMethod() { return refundMethod; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getReference() { return reference; }
    public String getNotes() { return notes; }
    public AppUser getRecordedBy() { return recordedBy; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
