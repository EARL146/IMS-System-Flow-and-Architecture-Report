package com.ims.api.purchasing;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "supplier_credit_notes")
public class SupplierCreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_id", nullable = false)
    private SupplierReturn supplierReturn;

    @Column(name = "supplier_reference", nullable = false, length = 100)
    private String supplierReference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private AppUser recordedBy;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected SupplierCreditNote() {
    }

    public SupplierCreditNote(String supplierReference, BigDecimal amount, String currencyCode, String notes,
                              AppUser recordedBy, String idempotencyKey, String requestHash, Instant recordedAt) {
        this.supplierReference = supplierReference;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.recordedAt = recordedAt;
    }

    void setSupplierReturn(SupplierReturn supplierReturn) { this.supplierReturn = supplierReturn; }
    public Long getId() { return id; }
    public SupplierReturn getSupplierReturn() { return supplierReturn; }
    public String getSupplierReference() { return supplierReference; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getNotes() { return notes; }
    public AppUser getRecordedBy() { return recordedBy; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getRecordedAt() { return recordedAt; }
}
