package com.ims.api.returns;

import com.ims.api.catalog.Product;
import com.ims.api.customer.CustomerOrderItem;
import com.ims.api.pos.PosSaleItem;
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

import java.math.BigDecimal;

@Entity
@Table(name = "return_case_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_case_item_line", columnNames = {"return_case_id", "line_number"})
})
public class ReturnCaseItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_case_id", nullable = false)
    private ReturnCase returnCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_item_id")
    private CustomerOrderItem customerOrderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pos_sale_item_id")
    private PosSaleItem posSaleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "line_number", nullable = false)
    private short lineNumber;

    @Column(name = "sku_snapshot", nullable = false, length = 80)
    private String sku;

    @Column(name = "product_name_snapshot", nullable = false, length = 180)
    private String productName;

    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 32)
    private String unitOfMeasure;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal requestedQuantity;

    @Column(name = "approved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal approvedQuantity = BigDecimal.ZERO;

    @Column(name = "restock_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal restockQuantity = BigDecimal.ZERO;

    @Column(name = "damaged_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @Column(name = "scrap_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQuantity = BigDecimal.ZERO;

    protected ReturnCaseItem() {
    }

    public ReturnCaseItem(short lineNumber, CustomerOrderItem source, BigDecimal requestedQuantity) {
        this.lineNumber = lineNumber;
        this.customerOrderItem = source;
        this.product = source.getProduct();
        this.sku = source.getSku();
        this.productName = source.getProductName();
        this.unitOfMeasure = source.getUnitOfMeasure();
        this.unitPrice = source.getUnitPrice();
        this.requestedQuantity = requestedQuantity;
        this.approvedQuantity = BigDecimal.ZERO.setScale(3);
        this.restockQuantity = BigDecimal.ZERO.setScale(3);
        this.damagedQuantity = BigDecimal.ZERO.setScale(3);
        this.scrapQuantity = BigDecimal.ZERO.setScale(3);
    }

    public ReturnCaseItem(short lineNumber, PosSaleItem source, BigDecimal requestedQuantity) {
        this.lineNumber = lineNumber;
        this.posSaleItem = source;
        this.product = source.getProduct();
        this.sku = source.getSku();
        this.productName = source.getProductName();
        this.unitOfMeasure = source.getUnitOfMeasure();
        this.unitPrice = source.getUnitPrice();
        this.requestedQuantity = requestedQuantity;
        this.approvedQuantity = BigDecimal.ZERO.setScale(3);
        this.restockQuantity = BigDecimal.ZERO.setScale(3);
        this.damagedQuantity = BigDecimal.ZERO.setScale(3);
        this.scrapQuantity = BigDecimal.ZERO.setScale(3);
    }

    void setReturnCase(ReturnCase returnCase) { this.returnCase = returnCase; }

    public void approve(BigDecimal quantity) { this.approvedQuantity = quantity; }

    public void inspect(BigDecimal restock, BigDecimal damaged, BigDecimal scrap) {
        this.restockQuantity = restock;
        this.damagedQuantity = damaged;
        this.scrapQuantity = scrap;
    }

    public Long getSourceItemId() {
        return customerOrderItem == null ? posSaleItem.getId() : customerOrderItem.getId();
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public CustomerOrderItem getCustomerOrderItem() { return customerOrderItem; }
    public PosSaleItem getPosSaleItem() { return posSaleItem; }
    public Product getProduct() { return product; }
    public short getLineNumber() { return lineNumber; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getApprovedQuantity() { return approvedQuantity; }
    public BigDecimal getRestockQuantity() { return restockQuantity; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public BigDecimal getScrapQuantity() { return scrapQuantity; }
}
