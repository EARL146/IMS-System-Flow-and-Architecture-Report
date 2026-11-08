package com.ims.api.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupplierReturnDtos {
    private SupplierReturnDtos() {
    }

    public record CreateRequest(
            @NotNull UUID idempotencyKey,
            @NotNull Long purchaseOrderId,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 1000) String notes,
            @NotEmpty @Size(max = 200) List<@Valid LineRequest> items
    ) {
    }

    public record LineRequest(
            @NotNull Long purchaseOrderItemId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 3) BigDecimal damagedQuantity
    ) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CreditNoteRequest(
            @NotNull UUID idempotencyKey,
            @NotBlank @Size(max = 100) String supplierReference,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 500) String notes
    ) {
    }

    public record ReturnableItemResponse(
            Long purchaseOrderItemId,
            Long productId,
            String sku,
            String description,
            BigDecimal receivedQuantity,
            BigDecimal returnedQuantity,
            BigDecimal returnableQuantity,
            BigDecimal unitCost
    ) {
        static ReturnableItemResponse from(PurchaseOrderItem item, BigDecimal returned) {
            return new ReturnableItemResponse(item.getId(), item.getProduct().getId(), item.getProduct().getSku(),
                    item.getDescription(), item.getReceivedQuantity(), returned,
                    item.getReceivedQuantity().subtract(returned), item.getUnitCost());
        }
    }

    public record ItemResponse(
            Long id, Long purchaseOrderItemId, Long productId, String sku, String description,
            BigDecimal quantity, BigDecimal damagedQuantity, BigDecimal unitCost, BigDecimal lineTotal
    ) {
        static ItemResponse from(SupplierReturnItem item) {
            PurchaseOrderItem orderItem = item.getPurchaseOrderItem();
            return new ItemResponse(item.getId(), orderItem.getId(), orderItem.getProduct().getId(),
                    orderItem.getProduct().getSku(), orderItem.getDescription(), item.getQuantity(),
                    item.getDamagedQuantity(), item.getUnitCost(), item.getQuantity().multiply(item.getUnitCost()));
        }
    }

    public record CreditNoteResponse(
            Long id, String supplierReference, BigDecimal amount, String currencyCode,
            String notes, String recordedBy, Instant recordedAt
    ) {
        static CreditNoteResponse from(SupplierCreditNote note) {
            return new CreditNoteResponse(note.getId(), note.getSupplierReference(), note.getAmount(),
                    note.getCurrencyCode(), note.getNotes(), note.getRecordedBy().getEmail(), note.getRecordedAt());
        }
    }

    public record Response(
            Long id, String returnNumber, Long purchaseOrderId, String orderNumber, String supplierName,
            String warehouseName, SupplierReturnStatus status, String reason, String notes,
            String rejectionReason, String currencyCode, BigDecimal expectedCreditAmount,
            BigDecimal creditedAmount, String requestedBy, String reviewedBy, String dispatchedBy, String cancelledBy,
            Instant requestedAt, Instant reviewedAt, Instant dispatchedAt, Instant cancelledAt,
            List<ItemResponse> items, List<CreditNoteResponse> creditNotes
    ) {
        static Response from(SupplierReturn supplierReturn) {
            BigDecimal credited = supplierReturn.getCreditNotes().stream()
                    .map(SupplierCreditNote::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            PurchaseOrder order = supplierReturn.getPurchaseOrder();
            return new Response(supplierReturn.getId(), supplierReturn.getReturnNumber(), order.getId(),
                    order.getOrderNumber(), order.getSupplier().getName(), order.getWarehouse().getName(),
                    supplierReturn.getStatus(), supplierReturn.getReason(), supplierReturn.getNotes(),
                    supplierReturn.getRejectionReason(), supplierReturn.getCurrencyCode(),
                    supplierReturn.getExpectedCreditAmount(), credited, supplierReturn.getRequestedBy().getEmail(),
                    supplierReturn.getReviewedBy() == null ? null : supplierReturn.getReviewedBy().getEmail(),
                    supplierReturn.getDispatchedBy() == null ? null : supplierReturn.getDispatchedBy().getEmail(),
                    supplierReturn.getCancelledBy() == null ? null : supplierReturn.getCancelledBy().getEmail(),
                    supplierReturn.getRequestedAt(), supplierReturn.getReviewedAt(), supplierReturn.getDispatchedAt(),
                    supplierReturn.getCancelledAt(),
                    supplierReturn.getItems().stream().map(ItemResponse::from).toList(),
                    supplierReturn.getCreditNotes().stream().map(CreditNoteResponse::from).toList());
        }
    }
}
