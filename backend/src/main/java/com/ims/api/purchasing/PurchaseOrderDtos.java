package com.ims.api.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseOrderDtos {
    private PurchaseOrderDtos() {
    }

    public record CreateRequest(
            @NotNull Long supplierId,
            @NotNull Long warehouseId,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            LocalDate expectedDate,
            @Size(max = 5000) String notes,
            @NotEmpty @Size(max = 200) List<@Valid LineRequest> items
    ) {
    }

    public record LineRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal unitCost
    ) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ReceiveRequest(
            @NotNull UUID idempotencyKey,
            @Size(max = 100) String deliveryReference,
            @Size(max = 500) String notes,
            @NotEmpty @Size(max = 200) List<@Valid ReceiveLineRequest> items
    ) {
    }

    public record ReceiveLineRequest(
            @NotNull Long purchaseOrderItemId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
    ) {
    }

    public record ItemResponse(
            Long id, short lineNumber, Long productId, String sku, String description,
            BigDecimal orderedQuantity, BigDecimal receivedQuantity, BigDecimal outstandingQuantity,
            BigDecimal unitCost, BigDecimal lineTotal
    ) {
        static ItemResponse from(PurchaseOrderItem item) {
            return new ItemResponse(item.getId(), item.getLineNumber(), item.getProduct().getId(),
                    item.getProduct().getSku(), item.getDescription(), item.getOrderedQuantity(),
                    item.getReceivedQuantity(), item.getOutstandingQuantity(), item.getUnitCost(),
                    item.getOrderedQuantity().multiply(item.getUnitCost()));
        }
    }

    public record Response(
            Long id, String orderNumber, Long supplierId, String supplierName, Long warehouseId,
            String warehouseName, PurchaseOrderStatus status, String currencyCode, LocalDate expectedDate,
            String notes, String rejectionReason, String createdBy, String reviewedBy,
            Instant reviewedAt, Instant orderedAt, Instant createdAt, BigDecimal total,
            BigDecimal receivedValue, String cancellationReason, String cancelledBy, Instant cancelledAt,
            BigDecimal cancelledUnreceivedValue,
            List<ItemResponse> items
    ) {
        static Response from(PurchaseOrder order) {
            return new Response(order.getId(), order.getOrderNumber(), order.getSupplier().getId(),
                    order.getSupplier().getName(), order.getWarehouse().getId(), order.getWarehouse().getName(),
                    order.getStatus(), order.getCurrencyCode(), order.getExpectedDate(), order.getNotes(),
                    order.getRejectionReason(), order.getCreatedBy().getEmail(),
                    order.getReviewedBy() == null ? null : order.getReviewedBy().getEmail(),
                    order.getReviewedAt(), order.getOrderedAt(), order.getCreatedAt(), order.getTotal(),
                    order.getReceivedValue(), order.getCancellationReason(),
                    order.getCancelledBy() == null ? null : order.getCancelledBy().getEmail(),
                    order.getCancelledAt(), order.getCancelledUnreceivedValue(),
                    order.getItems().stream().map(ItemResponse::from).toList());
        }
    }

    public record ReceiptItemResponse(
            Long id, Long purchaseOrderItemId, Long productId, String sku,
            BigDecimal quantityReceived, BigDecimal unitCost
    ) {
        static ReceiptItemResponse from(GoodsReceiptItem item) {
            PurchaseOrderItem orderItem = item.getPurchaseOrderItem();
            return new ReceiptItemResponse(item.getId(), orderItem.getId(), orderItem.getProduct().getId(),
                    orderItem.getProduct().getSku(), item.getQuantityReceived(), item.getUnitCost());
        }
    }

    public record ReceiptResponse(
            Long id, Long purchaseOrderId, String orderNumber, String idempotencyKey,
            String deliveryReference, String receivedBy, Instant receivedAt, String notes,
            List<ReceiptItemResponse> items
    ) {
        static ReceiptResponse from(GoodsReceipt receipt) {
            return new ReceiptResponse(receipt.getId(), receipt.getPurchaseOrder().getId(),
                    receipt.getPurchaseOrder().getOrderNumber(), receipt.getIdempotencyKey(),
                    receipt.getDeliveryReference(), receipt.getReceivedBy().getEmail(), receipt.getReceivedAt(),
                    receipt.getNotes(), receipt.getItems().stream().map(ReceiptItemResponse::from).toList());
        }
    }
}
