package com.ims.api.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 80) String sku,
            @Size(max = 80) String barcode,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 5000) String description,
            Long categoryId,
            @NotBlank @Size(max = 32) String unitOfMeasure,
            @NotNull TrackingType trackingType,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal costPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal sellingPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 3) BigDecimal reorderLevel
    ) {
    }

    public record CatalogProductResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            String description,
            String categoryName,
            String unitOfMeasure,
            TrackingType trackingType,
            BigDecimal sellingPrice,
            boolean active
    ) {
        static CatalogProductResponse from(Product product) {
            return new CatalogProductResponse(
                    product.getId(), product.getSku(), product.getBarcode(), product.getName(),
                    product.getDescription(), product.getCategory() == null ? null : product.getCategory().getName(),
                    product.getUnitOfMeasure(), product.getTrackingType(), product.getSellingPrice(), product.isActive());
        }
    }

    public record AdminProductResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            String description,
            Long categoryId,
            String categoryName,
            String unitOfMeasure,
            TrackingType trackingType,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            BigDecimal reorderLevel,
            boolean active
    ) {
        static AdminProductResponse from(Product product) {
            return new AdminProductResponse(
                    product.getId(), product.getSku(), product.getBarcode(), product.getName(),
                    product.getDescription(), product.getCategory() == null ? null : product.getCategory().getId(),
                    product.getCategory() == null ? null : product.getCategory().getName(), product.getUnitOfMeasure(),
                    product.getTrackingType(), product.getCostPrice(), product.getSellingPrice(),
                    product.getReorderLevel(), product.isActive());
        }
    }

    public record CategoryRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record CategoryResponse(Long id, String name, boolean active) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.isActive());
        }
    }
}
