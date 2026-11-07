package com.ims.api.customer;

import com.ims.api.common.PageResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 160) String recipientName,
            @NotBlank @Size(max = 40) String phone,
            @NotBlank @Size(max = 180) String addressLine1,
            @Size(max = 180) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 24) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            boolean defaultAddress
    ) {
    }

    public record AddressResponse(
            Long id, String label, String recipientName, String phone, String addressLine1,
            String addressLine2, String city, String region, String postalCode,
            String countryCode, boolean defaultAddress, Instant createdAt
    ) {
        static AddressResponse from(CustomerAddress address) {
            return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                    address.getPhone(), address.getAddressLine1(), address.getAddressLine2(),
                    address.getCity(), address.getRegion(), address.getPostalCode(),
                    address.getCountryCode(), address.isDefaultAddress(), address.getCreatedAt());
        }
    }

    public record AddCartItemRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
    ) {
    }

    public record UpdateCartItemRequest(
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
    ) {
    }

    public record CartLineResponse(
            Long id, Long productId, String sku, String name, String unitOfMeasure,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal, boolean active
    ) {
        static CartLineResponse from(CartItem item) {
            BigDecimal unitPrice = item.getProduct().getSellingPrice();
            BigDecimal lineTotal = unitPrice.multiply(item.getQuantity()).setScale(4, java.math.RoundingMode.HALF_UP);
            return new CartLineResponse(item.getId(), item.getProduct().getId(), item.getProduct().getSku(),
                    item.getProduct().getName(), item.getProduct().getUnitOfMeasure(), item.getQuantity(),
                    unitPrice, lineTotal, item.getProduct().isActive());
        }
    }

    public record CartResponse(List<CartLineResponse> items, BigDecimal subtotal, BigDecimal totalQuantity) {
        static CartResponse from(List<CartItem> items) {
            List<CartLineResponse> lines = items.stream().map(CartLineResponse::from).toList();
            BigDecimal subtotal = lines.stream().map(CartLineResponse::lineTotal)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            BigDecimal quantity = lines.stream().map(CartLineResponse::quantity)
                    .reduce(BigDecimal.ZERO.setScale(3), BigDecimal::add);
            return new CartResponse(lines, subtotal, quantity);
        }
    }

    public record CheckoutRequest(
            @NotNull UUID idempotencyKey,
            @NotNull Long addressId,
            @NotNull Long warehouseId,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 1000) String notes
    ) {
    }

    public record OrderItemResponse(
            Long id, short lineNumber, Long productId, String sku, String productName,
            String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal
    ) {
        static OrderItemResponse from(CustomerOrderItem item) {
            return new OrderItemResponse(item.getId(), item.getLineNumber(), item.getProduct().getId(),
                    item.getSku(), item.getProductName(), item.getUnitOfMeasure(), item.getQuantity(),
                    item.getUnitPrice(), item.getLineTotal());
        }
    }

    public record OrderResponse(
            Long id, String orderNumber, Long warehouseId, String warehouseName,
            CustomerOrderStatus status, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
            String currencyCode, BigDecimal subtotal, BigDecimal taxAmount,
            BigDecimal shippingAmount, BigDecimal discountAmount, BigDecimal total,
            String addressLabel, String recipientName, String recipientPhone,
            String addressLine1, String addressLine2, String city, String region,
            String postalCode, String countryCode, String notes, Instant cancelledAt,
            Instant createdAt, List<OrderItemResponse> items
    ) {
        static OrderResponse from(CustomerOrder order) {
            return new OrderResponse(order.getId(), order.getOrderNumber(), order.getWarehouse().getId(),
                    order.getWarehouse().getName(), order.getStatus(), order.getPaymentMethod(),
                    order.getPaymentStatus(), order.getCurrencyCode(), order.getSubtotal(),
                    order.getTaxAmount(), order.getShippingAmount(), order.getDiscountAmount(),
                    order.getTotal(), order.getAddressLabel(), order.getRecipientName(),
                    order.getRecipientPhone(), order.getAddressLine1(), order.getAddressLine2(),
                    order.getCity(), order.getRegion(), order.getPostalCode(), order.getCountryCode(),
                    order.getNotes(), order.getCancelledAt(), order.getCreatedAt(),
                    order.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record PublicWarehouseResponse(Long id, String code, String name) {
        public static PublicWarehouseResponse from(com.ims.api.warehouse.Warehouse warehouse) {
            return new PublicWarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName());
        }
    }
}
