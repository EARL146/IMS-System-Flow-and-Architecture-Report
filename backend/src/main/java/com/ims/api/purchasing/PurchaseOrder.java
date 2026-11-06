package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import com.ims.api.warehouse.Warehouse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseOrderStatus status;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String orderNumber, Supplier supplier, Warehouse warehouse,
                         String currencyCode, LocalDate expectedDate, String notes, AppUser createdBy) {
        this.orderNumber = orderNumber;
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.status = PurchaseOrderStatus.DRAFT;
        this.currencyCode = currencyCode;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void addItem(PurchaseOrderItem item) {
        item.setPurchaseOrder(this);
        items.add(item);
    }

    public void submit() {
        requireStatus(PurchaseOrderStatus.DRAFT);
        if (items.isEmpty()) throw new IllegalStateException("A purchase order must contain at least one item");
        status = PurchaseOrderStatus.PENDING_APPROVAL;
    }

    public void approve(AppUser reviewer, Instant at) {
        requireStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        status = PurchaseOrderStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = null;
    }

    public void reject(AppUser reviewer, Instant at, String reason) {
        requireStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        status = PurchaseOrderStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = reason;
    }

    public void markOrdered(Instant at) {
        requireStatus(PurchaseOrderStatus.APPROVED);
        status = PurchaseOrderStatus.ORDERED;
        orderedAt = at;
    }

    public void cancel() {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.PENDING_APPROVAL
                && status != PurchaseOrderStatus.APPROVED && status != PurchaseOrderStatus.REJECTED) {
            throw new IllegalStateException("An order cannot be cancelled after it has been sent to the supplier");
        }
        status = PurchaseOrderStatus.CANCELLED;
    }

    public void refreshReceiptStatus() {
        if (items.stream().allMatch(item -> item.getOutstandingQuantity().signum() == 0)) {
            status = PurchaseOrderStatus.RECEIVED;
        } else {
            status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        }
    }

    private void requireStatus(PurchaseOrderStatus expected) {
        if (status != expected) throw new IllegalStateException("Purchase order must be " + expected + " to perform this action");
    }

    public BigDecimal getTotal() {
        return items.stream().map(item -> item.getUnitCost().multiply(item.getOrderedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Supplier getSupplier() { return supplier; }
    public Warehouse getWarehouse() { return warehouse; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public String getNotes() { return notes; }
    public String getRejectionReason() { return rejectionReason; }
    public AppUser getCreatedBy() { return createdBy; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PurchaseOrderItem> getItems() { return items; }
}
