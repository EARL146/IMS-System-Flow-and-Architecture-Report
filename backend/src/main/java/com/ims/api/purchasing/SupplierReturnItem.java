package com.ims.api.purchasing;

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
@Table(name = "supplier_return_items")
public class SupplierReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_id", nullable = false)
    private SupplierReturn supplierReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "damaged_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal damagedQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    protected SupplierReturnItem() {
    }

    public SupplierReturnItem(short lineNumber, PurchaseOrderItem purchaseOrderItem,
                              BigDecimal quantity, BigDecimal damagedQuantity) {
        this.lineNumber = lineNumber;
        this.purchaseOrderItem = purchaseOrderItem;
        this.quantity = quantity;
        this.damagedQuantity = damagedQuantity;
        this.unitCost = purchaseOrderItem.getUnitCost();
    }

    void setSupplierReturn(SupplierReturn supplierReturn) { this.supplierReturn = supplierReturn; }
    public Long getId() { return id; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public short getLineNumber() { return lineNumber; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
