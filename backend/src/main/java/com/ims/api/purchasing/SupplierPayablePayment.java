package com.ims.api.purchasing;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "supplier_payable_payments")
public class SupplierPayablePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payable_id", nullable = false)
    private SupplierPayable payable;

    @Column(name = "payment_reference", nullable = false, length = 120)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private SupplierPayablePaymentMethod paymentMethod;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(length = 500)
    private String notes;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private AppUser recordedBy;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected SupplierPayablePayment() {
    }

    public SupplierPayablePayment(String paymentReference, SupplierPayablePaymentMethod paymentMethod,
                                  BigDecimal amount, String currencyCode, String notes,
                                  String idempotencyKey, String requestHash, AppUser recordedBy, Instant paidAt) {
        this.paymentReference = paymentReference;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.recordedBy = recordedBy;
        this.paidAt = paidAt;
    }

    void setPayable(SupplierPayable payable) {
        this.payable = payable;
    }

    public Long getId() { return id; }
    public SupplierPayable getPayable() { return payable; }
    public String getPaymentReference() { return paymentReference; }
    public SupplierPayablePaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getNotes() { return notes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public AppUser getRecordedBy() { return recordedBy; }
    public Instant getPaidAt() { return paidAt; }
}
