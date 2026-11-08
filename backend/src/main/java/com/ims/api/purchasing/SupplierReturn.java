package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
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
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "supplier_returns")
public class SupplierReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_number", nullable = false, unique = true, length = 40)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierReturnStatus status;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "expected_credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedCreditAmount = BigDecimal.ZERO;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private AppUser reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by_user_id")
    private AppUser dispatchedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private AppUser cancelledBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<SupplierReturnItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt ASC, id ASC")
    private Set<SupplierCreditNote> creditNotes = new LinkedHashSet<>();

    protected SupplierReturn() {
    }

    public SupplierReturn(String returnNumber, PurchaseOrder purchaseOrder, String reason, String notes,
                          String idempotencyKey, String requestHash, AppUser requestedBy, Instant requestedAt) {
        this.returnNumber = returnNumber;
        this.purchaseOrder = purchaseOrder;
        this.status = SupplierReturnStatus.PENDING_APPROVAL;
        this.reason = reason;
        this.notes = notes;
        this.currencyCode = purchaseOrder.getCurrencyCode();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public void addItem(SupplierReturnItem item) {
        item.setSupplierReturn(this);
        items.add(item);
        expectedCreditAmount = expectedCreditAmount.add(item.getUnitCost().multiply(item.getQuantity()));
    }

    public void approve(AppUser reviewer, Instant at) {
        requireStatus(SupplierReturnStatus.PENDING_APPROVAL);
        status = SupplierReturnStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = null;
    }

    public void reject(AppUser reviewer, Instant at, String reason) {
        requireStatus(SupplierReturnStatus.PENDING_APPROVAL);
        status = SupplierReturnStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = reason;
    }

    public void dispatch(AppUser dispatcher, Instant at) {
        requireStatus(SupplierReturnStatus.APPROVED);
        status = SupplierReturnStatus.DISPATCHED;
        dispatchedBy = dispatcher;
        dispatchedAt = at;
    }

    public void cancel(AppUser actor, Instant at) {
        if (status != SupplierReturnStatus.PENDING_APPROVAL && status != SupplierReturnStatus.APPROVED) {
            throw new IllegalStateException("Only a pending or approved supplier return can be cancelled");
        }
        status = SupplierReturnStatus.CANCELLED;
        cancelledBy = actor;
        cancelledAt = at;
    }

    public void addCreditNote(SupplierCreditNote creditNote) {
        if (status != SupplierReturnStatus.DISPATCHED) {
            throw new IllegalStateException("Supplier credit notes can be recorded only after the return is dispatched");
        }
        creditNote.setSupplierReturn(this);
        creditNotes.add(creditNote);
    }

    private void requireStatus(SupplierReturnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Supplier return must be " + expected + " to perform this action");
        }
    }

    public Long getId() { return id; }
    public String getReturnNumber() { return returnNumber; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public SupplierReturnStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public String getRejectionReason() { return rejectionReason; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExpectedCreditAmount() { return expectedCreditAmount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public AppUser getRequestedBy() { return requestedBy; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public AppUser getDispatchedBy() { return dispatchedBy; }
    public AppUser getCancelledBy() { return cancelledBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<SupplierReturnItem> getItems() { return items; }
    public Set<SupplierCreditNote> getCreditNotes() { return creditNotes; }
}
