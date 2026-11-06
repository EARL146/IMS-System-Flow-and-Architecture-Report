package com.ims.api.catalog;

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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(length = 80, unique = true)
    private String barcode;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "unit_of_measure", nullable = false, length = 32)
    private String unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false, length = 32)
    private TrackingType trackingType;

    @Column(name = "cost_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 3)
    private BigDecimal reorderLevel;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(
            String sku,
            String barcode,
            String name,
            String description,
            Category category,
            String unitOfMeasure,
            TrackingType trackingType,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            BigDecimal reorderLevel
    ) {
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.unitOfMeasure = unitOfMeasure;
        this.trackingType = trackingType;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.reorderLevel = reorderLevel;
        this.active = true;
    }

    public void update(
            String sku,
            String barcode,
            String name,
            String description,
            Category category,
            String unitOfMeasure,
            TrackingType trackingType,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            BigDecimal reorderLevel
    ) {
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.unitOfMeasure = unitOfMeasure;
        this.trackingType = trackingType;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.reorderLevel = reorderLevel;
    }

    public void archive() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public TrackingType getTrackingType() {
        return trackingType;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
