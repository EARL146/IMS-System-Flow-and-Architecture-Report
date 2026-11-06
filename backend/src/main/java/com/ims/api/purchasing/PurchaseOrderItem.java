package com.ims.api.purchasing;

import com.ims.api.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 180)
    private String description;

    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQuantity;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(short lineNumber, Product product, BigDecimal orderedQuantity, BigDecimal unitCost) {
        this.lineNumber = lineNumber;
        this.product = product;
        this.description = product.getName();
        this.orderedQuantity = orderedQuantity;
        this.receivedQuantity = BigDecimal.ZERO;
        this.unitCost = unitCost;
    }

    void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public BigDecimal receive(BigDecimal quantity) {
        BigDecimal remaining = orderedQuantity.subtract(receivedQuantity);
        if (quantity.signum() <= 0 || quantity.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Received quantity must be positive and cannot exceed the outstanding quantity");
        }
        receivedQuantity = receivedQuantity.add(quantity);
        return receivedQuantity;
    }

    public Long getId() { return id; }
    public short getLineNumber() { return lineNumber; }
    public Product getProduct() { return product; }
    public String getDescription() { return description; }
    public BigDecimal getOrderedQuantity() { return orderedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getOutstandingQuantity() { return orderedQuantity.subtract(receivedQuantity); }
    public BigDecimal getUnitCost() { return unitCost; }
}
