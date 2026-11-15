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
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "supplier_payables")
public class SupplierPayable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payable_number", nullable = false, unique = true, length = 40)
    private String payableNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "invoice_reference", length = 120)
    private String invoiceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierPayableStatus status;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "credit_applied_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAppliedAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "payable", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidAt ASC, id ASC")
    private List<SupplierPayablePayment> payments = new ArrayList<>();

    protected SupplierPayable() {
    }

    public SupplierPayable(String payableNumber, PurchaseOrder purchaseOrder, Supplier supplier,
                           String invoiceReference, String currencyCode, BigDecimal grossAmount,
                           BigDecimal creditAppliedAmount, LocalDate dueDate, String notes, AppUser createdBy) {
        this.payableNumber = payableNumber;
        this.purchaseOrder = purchaseOrder;
        this.supplier = supplier;
        this.invoiceReference = invoiceReference;
        this.currencyCode = currencyCode;
        this.grossAmount = grossAmount;
        this.creditAppliedAmount = creditAppliedAmount == null ? BigDecimal.ZERO : creditAppliedAmount;
        this.amountPaid = BigDecimal.ZERO;
        this.dueDate = dueDate;
        this.notes = notes;
        this.createdBy = createdBy;
        this.status = SupplierPayableStatus.OPEN;
        recalculateStatus();
    }

    public BigDecimal getNetPayableAmount() {
        BigDecimal net = grossAmount.subtract(creditAppliedAmount);
        return net.signum() < 0 ? BigDecimal.ZERO : net;
    }

    public BigDecimal getBalanceDue() {
        BigDecimal due = getNetPayableAmount().subtract(amountPaid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    public void updateGrossAndCredits(BigDecimal newGrossAmount, BigDecimal totalAvailableCredits) {
        if (status == SupplierPayableStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled supplier payable cannot be modified");
        }
        this.grossAmount = newGrossAmount;
        BigDecimal cappedCredits = totalAvailableCredits.min(newGrossAmount);
        if (cappedCredits.signum() < 0) {
            cappedCredits = BigDecimal.ZERO;
        }
        this.creditAppliedAmount = cappedCredits;
        recalculateStatus();
    }

    public void addPayment(SupplierPayablePayment payment) {
        if (status == SupplierPayableStatus.CANCELLED || status == SupplierPayableStatus.SETTLED) {
            throw new IllegalStateException("Payments can only be recorded against an open or partially paid supplier payable");
        }
        if (payment.getAmount().compareTo(getBalanceDue()) > 0) {
            throw new IllegalStateException("Payment amount exceeds remaining balance due");
        }
        payment.setPayable(this);
        payments.add(payment);
        this.amountPaid = this.amountPaid.add(payment.getAmount());
        recalculateStatus();
    }

    public void recalculateStatus() {
        if (status == SupplierPayableStatus.CANCELLED) {
            return;
        }
        BigDecimal balance = getBalanceDue();
        if (balance.signum() == 0 && (grossAmount.signum() > 0 || amountPaid.signum() > 0 || creditAppliedAmount.signum() > 0)) {
            status = SupplierPayableStatus.SETTLED;
        } else if (amountPaid.signum() > 0 || creditAppliedAmount.signum() > 0) {
            status = SupplierPayableStatus.PARTIALLY_PAID;
        } else {
            status = SupplierPayableStatus.OPEN;
        }
    }

    public Long getId() { return id; }
    public String getPayableNumber() { return payableNumber; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public Supplier getSupplier() { return supplier; }
    public String getInvoiceReference() { return invoiceReference; }
    public SupplierPayableStatus getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getCreditAppliedAmount() { return creditAppliedAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public LocalDate getDueDate() { return dueDate; }
    public String getNotes() { return notes; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<SupplierPayablePayment> getPayments() { return payments; }
}
