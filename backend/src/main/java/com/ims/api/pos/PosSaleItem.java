package com.ims.api.pos;

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
@Table(name = "pos_sale_items", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uq_pos_sale_item_line", columnNames = {"pos_sale_id", "line_number"})
})
public class PosSaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pos_sale_id", nullable = false)
    private PosSale sale;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku_snapshot", nullable = false, length = 80)
    private String sku;

    @Column(name = "product_name_snapshot", nullable = false, length = 180)
    private String productName;

    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 32)
    private String unitOfMeasure;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    protected PosSaleItem() {
    }

    public PosSaleItem(short lineNumber, Product product, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal unitCost, BigDecimal lineTotal) {
        this.lineNumber = lineNumber;
        this.product = product;
        this.sku = product.getSku();
        this.productName = product.getName();
        this.unitOfMeasure = product.getUnitOfMeasure();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.lineTotal = lineTotal;
    }

    void setSale(PosSale sale) { this.sale = sale; }
    public Long getId() { return id; }
    public PosSale getSale() { return sale; }
    public short getLineNumber() { return lineNumber; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
