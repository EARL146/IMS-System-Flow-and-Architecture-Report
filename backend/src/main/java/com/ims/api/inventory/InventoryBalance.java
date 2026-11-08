package com.ims.api.inventory;

import com.ims.api.catalog.Product;
import com.ims.api.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_balances", uniqueConstraints = {
        @UniqueConstraint(name = "uq_inventory_balance_product_warehouse",
                columnNames = {"product_id", "warehouse_id"})
})
public class InventoryBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "on_hand_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal onHandQuantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "damaged_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryBalance() {
    }

    public InventoryBalance(Product product, Warehouse warehouse) {
        this.product = product;
        this.warehouse = warehouse;
        this.onHandQuantity = BigDecimal.ZERO;
        this.reservedQuantity = BigDecimal.ZERO;
        this.damagedQuantity = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public BigDecimal getOnHandQuantity() {
        return onHandQuantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal getDamagedQuantity() {
        return damagedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return onHandQuantity.subtract(reservedQuantity).subtract(damagedQuantity);
    }

    public void setOnHandQuantity(BigDecimal onHandQuantity) {
        this.onHandQuantity = onHandQuantity;
    }

    public void setReservedQuantity(BigDecimal reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public void setDamagedQuantity(BigDecimal damagedQuantity) {
        this.damagedQuantity = damagedQuantity;
    }
}
