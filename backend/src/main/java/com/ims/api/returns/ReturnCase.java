package com.ims.api.returns;

import com.ims.api.auth.AppUser;
import com.ims.api.customer.CustomerOrder;
import com.ims.api.pos.PosSale;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "return_cases", uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_cases_number", columnNames = "return_number"),
        @UniqueConstraint(name = "uq_return_cases_idempotency", columnNames = "idempotency_key")
})
public class ReturnCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_number", nullable = false, length = 40)
    private String returnNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private ReturnSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_id")
    private CustomerOrder customerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pos_sale_id")
    private PosSale posSale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_user_id")
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private AppUser reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspected_by_user_id")
    private AppUser inspectedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReturnStatus status;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @OneToMany(mappedBy = "returnCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ReturnCaseItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "returnCase", fetch = FetchType.LAZY)
    private ReturnRefund refund;

    protected ReturnCase() {
    }

    public ReturnCase(String returnNumber, ReturnSourceType sourceType, CustomerOrder customerOrder,
                      PosSale posSale, AppUser customer, AppUser requestedBy, String reason,
                      String notes, String currencyCode, String idempotencyKey) {
        this.returnNumber = returnNumber;
        this.sourceType = sourceType;
        this.customerOrder = customerOrder;
        this.posSale = posSale;
        this.customer = customer;
        this.requestedBy = requestedBy;
        this.status = ReturnStatus.REQUESTED;
        this.reason = reason;
        this.notes = notes;
        this.currencyCode = currencyCode;
        this.idempotencyKey = idempotencyKey;
        this.refundAmount = BigDecimal.ZERO.setScale(4);
    }

    public void addItem(ReturnCaseItem item) {
        item.setReturnCase(this);
        items.add(item);
    }

    public void approve(AppUser reviewer, Instant at) {
        status = ReturnStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = null;
    }

    public void reject(AppUser reviewer, String reason, Instant at) {
        status = ReturnStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = at;
        rejectionReason = reason;
    }

    public void inspect(AppUser inspector, BigDecimal computedRefundAmount, Instant at) {
        status = ReturnStatus.INSPECTED;
        inspectedBy = inspector;
        inspectedAt = at;
        refundAmount = computedRefundAmount;
    }

    public void markRefunded() {
        status = ReturnStatus.REFUNDED;
    }

    void setRefund(ReturnRefund refund) { this.refund = refund; }

    public Long getId() { return id; }
    public String getReturnNumber() { return returnNumber; }
    public ReturnSourceType getSourceType() { return sourceType; }
    public CustomerOrder getCustomerOrder() { return customerOrder; }
    public PosSale getPosSale() { return posSale; }
    public AppUser getCustomer() { return customer; }
    public AppUser getRequestedBy() { return requestedBy; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public AppUser getInspectedBy() { return inspectedBy; }
    public ReturnStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public String getRejectionReason() { return rejectionReason; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getInspectedAt() { return inspectedAt; }
    public List<ReturnCaseItem> getItems() { return items; }
    public ReturnRefund getRefund() { return refund; }

    public Long getSourceId() {
        return sourceType == ReturnSourceType.CUSTOMER_ORDER ? customerOrder.getId() : posSale.getId();
    }

    public String getSourceNumber() {
        return sourceType == ReturnSourceType.CUSTOMER_ORDER ? customerOrder.getOrderNumber() : posSale.getSaleNumber();
    }

    public Warehouse getWarehouse() {
        return sourceType == ReturnSourceType.CUSTOMER_ORDER ? customerOrder.getWarehouse() : posSale.getWarehouse();
    }
}
