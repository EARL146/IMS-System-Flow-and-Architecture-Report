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
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "quantity_received", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityReceived;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    protected GoodsReceiptItem() {
    }

    public GoodsReceiptItem(PurchaseOrderItem purchaseOrderItem, BigDecimal quantityReceived) {
        this.purchaseOrderItem = purchaseOrderItem;
        this.quantityReceived = quantityReceived;
        this.unitCost = purchaseOrderItem.getUnitCost();
    }

    void setReceipt(GoodsReceipt receipt) { this.receipt = receipt; }

    public Long getId() { return id; }
    public GoodsReceipt getReceipt() { return receipt; }
    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public BigDecimal getUnitCost() { return unitCost; }
}
