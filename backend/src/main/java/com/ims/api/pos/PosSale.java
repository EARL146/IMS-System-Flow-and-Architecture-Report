package com.ims.api.pos;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pos_sales", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pos_sales_number", columnNames = "sale_number")
})
public class PosSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_number", nullable = false, unique = true, length = 40)
    private String saleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_user_id", nullable = false)
    private AppUser cashier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_user_id")
    private AppUser customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PosPaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(name = "amount_tendered", precision = 19, scale = 4)
    private BigDecimal amountTendered;

    @Column(name = "change_given", nullable = false, precision = 19, scale = 4)
    private BigDecimal changeGiven;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<PosSaleItem> items = new ArrayList<>();

    protected PosSale() {
    }

    public PosSale(String saleNumber, AppUser cashier, Warehouse warehouse, AppUser customer,
                   PosPaymentMethod paymentMethod, String paymentReference, String currencyCode,
                   BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
                   BigDecimal total, BigDecimal amountTendered, BigDecimal changeGiven) {
        this.saleNumber = saleNumber;
        this.cashier = cashier;
        this.warehouse = warehouse;
        this.customer = customer;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
        this.currencyCode = currencyCode;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.total = total;
        this.amountTendered = amountTendered;
        this.changeGiven = changeGiven;
    }

    public void addItem(PosSaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getSaleNumber() { return saleNumber; }
    public AppUser getCashier() { return cashier; }
    public Warehouse getWarehouse() { return warehouse; }
    public AppUser getCustomer() { return customer; }
    public PosPaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getPaymentReference() { return paymentReference; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getAmountTendered() { return amountTendered; }
    public BigDecimal getChangeGiven() { return changeGiven; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PosSaleItem> getItems() { return items; }
}
