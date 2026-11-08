package com.ims.api.pos;

import com.ims.api.catalog.Product;
import com.ims.api.catalog.TrackingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PosDtos {
    private PosDtos() {
    }

    public record ConfigResponse(BigDecimal taxRate, String currencyCode) {
    }

    public record ProductOptionResponse(
            Long id, String sku, String barcode, String name, String categoryName,
            String unitOfMeasure, TrackingType trackingType, BigDecimal sellingPrice,
            BigDecimal availableQuantity
    ) {
        static ProductOptionResponse from(Product product, BigDecimal availableQuantity) {
            return new ProductOptionResponse(product.getId(), product.getSku(), product.getBarcode(),
                    product.getName(), product.getCategory() == null ? null : product.getCategory().getName(),
                    product.getUnitOfMeasure(), product.getTrackingType(), product.getSellingPrice(),
                    product.getTrackingType() == TrackingType.NONE ? null : availableQuantity);
        }
    }

    public record CustomerOptionResponse(Long id, String email, String fullName) {
        static CustomerOptionResponse from(com.ims.api.auth.AppUser user) {
            return new CustomerOptionResponse(user.getId(), user.getEmail(), user.getFullName());
        }
    }

    public record LineRequest(
            @NotNull @Min(1) Long productId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
    ) {
    }

    public record CreateSaleRequest(
            @NotNull UUID idempotencyKey,
            @NotNull @Min(1) Long warehouseId,
            @Email @Size(max = 254) String customerEmail,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid LineRequest> items,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal discountAmount,
            @NotNull PosPaymentMethod paymentMethod,
            @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal amountTendered,
            @Size(max = 100) String paymentReference
    ) {
    }

    public record SaleItemResponse(
            Long id, short lineNumber, Long productId, String sku, String productName,
            String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal
    ) {
        static SaleItemResponse from(PosSaleItem item) {
            return new SaleItemResponse(item.getId(), item.getLineNumber(), item.getProduct().getId(),
                    item.getSku(), item.getProductName(), item.getUnitOfMeasure(), item.getQuantity(),
                    item.getUnitPrice(), item.getLineTotal());
        }
    }

    public record SaleResponse(
            Long id, String saleNumber, String status, Instant createdAt,
            Long warehouseId, String warehouseName, String cashierName,
            Long customerId, String customerEmail, String customerName,
            PosPaymentMethod paymentMethod, String paymentStatus, String paymentReference,
            String currencyCode, BigDecimal subtotal, BigDecimal discountAmount,
            BigDecimal taxAmount, BigDecimal total, BigDecimal amountTendered,
            BigDecimal changeGiven, List<SaleItemResponse> items
    ) {
        static SaleResponse from(PosSale sale) {
            return new SaleResponse(sale.getId(), sale.getSaleNumber(), "COMPLETED", sale.getCreatedAt(),
                    sale.getWarehouse().getId(), sale.getWarehouse().getName(), sale.getCashier().getFullName(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getEmail(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getFullName(),
                    sale.getPaymentMethod(), sale.getPaymentStatus().name(), sale.getPaymentReference(), sale.getCurrencyCode(),
                    sale.getSubtotal(), sale.getDiscountAmount(), sale.getTaxAmount(), sale.getTotal(),
                    sale.getAmountTendered(), sale.getChangeGiven(),
                    sale.getItems().stream().map(SaleItemResponse::from).toList());
        }
    }
}
