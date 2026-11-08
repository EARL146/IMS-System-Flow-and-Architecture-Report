package com.ims.api.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReturnDtos {
    private ReturnDtos() {
    }

    public record CreateReturnRequest(
            @NotNull UUID idempotencyKey,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 1000) String notes,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid RequestedLine> items
    ) {
    }

    public record RequestedLine(
            @NotNull @Positive Long sourceItemId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
    ) {
    }

    public record ApproveReturnRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ApprovedLine> items
    ) {
    }

    public record ApprovedLine(
            @NotNull @Positive Long returnItemId,
            @NotNull @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal approvedQuantity
    ) {
    }

    public record RejectReturnRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record InspectReturnRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Valid InspectionLine> items
    ) {
    }

    public record InspectionLine(
            @NotNull @Positive Long returnItemId,
            @NotNull @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal restockQuantity,
            @NotNull @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal damagedQuantity,
            @NotNull @DecimalMin("0.000") @Digits(integer = 15, fraction = 3) BigDecimal scrapQuantity
    ) {
    }

    public record RefundRequest(
            @NotNull UUID idempotencyKey,
            @NotNull ReturnRefundMethod method,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes
    ) {
    }

    public record RefundResponse(
            Long id,
            String refundNumber,
            ReturnRefundMethod method,
            BigDecimal amount,
            String currencyCode,
            String reference,
            String notes,
            String recordedBy,
            Instant createdAt
    ) {
        static RefundResponse from(ReturnRefund refund) {
            return refund == null ? null : new RefundResponse(refund.getId(), refund.getRefundNumber(),
                    refund.getRefundMethod(), refund.getAmount(), refund.getCurrencyCode(), refund.getReference(),
                    refund.getNotes(), refund.getRecordedBy().getFullName(), refund.getCreatedAt());
        }
    }

    public record ReturnItemResponse(
            Long id,
            Long sourceItemId,
            Long productId,
            String sku,
            String productName,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal requestedQuantity,
            BigDecimal approvedQuantity,
            BigDecimal restockQuantity,
            BigDecimal damagedQuantity,
            BigDecimal scrapQuantity
    ) {
        static ReturnItemResponse from(ReturnCaseItem item) {
            return new ReturnItemResponse(item.getId(), item.getSourceItemId(), item.getProduct().getId(),
                    item.getSku(), item.getProductName(), item.getUnitOfMeasure(), item.getUnitPrice(),
                    item.getRequestedQuantity(), item.getApprovedQuantity(), item.getRestockQuantity(),
                    item.getDamagedQuantity(), item.getScrapQuantity());
        }
    }

    public record ReturnResponse(
            Long id,
            String returnNumber,
            ReturnSourceType sourceType,
            Long sourceId,
            String sourceNumber,
            Long customerId,
            String customerName,
            String customerEmail,
            ReturnStatus status,
            String reason,
            String notes,
            String rejectionReason,
            String currencyCode,
            BigDecimal refundAmount,
            String requestedBy,
            String reviewedBy,
            String inspectedBy,
            Instant requestedAt,
            Instant reviewedAt,
            Instant inspectedAt,
            List<ReturnItemResponse> items,
            RefundResponse refund
    ) {
        static ReturnResponse from(ReturnCase returnCase) {
            return new ReturnResponse(returnCase.getId(), returnCase.getReturnNumber(), returnCase.getSourceType(),
                    returnCase.getSourceId(), returnCase.getSourceNumber(),
                    returnCase.getCustomer() == null ? null : returnCase.getCustomer().getId(),
                    returnCase.getCustomer() == null ? null : returnCase.getCustomer().getFullName(),
                    returnCase.getCustomer() == null ? null : returnCase.getCustomer().getEmail(),
                    returnCase.getStatus(), returnCase.getReason(), returnCase.getNotes(),
                    returnCase.getRejectionReason(), returnCase.getCurrencyCode(), returnCase.getRefundAmount(),
                    returnCase.getRequestedBy().getFullName(),
                    returnCase.getReviewedBy() == null ? null : returnCase.getReviewedBy().getFullName(),
                    returnCase.getInspectedBy() == null ? null : returnCase.getInspectedBy().getFullName(),
                    returnCase.getRequestedAt(), returnCase.getReviewedAt(), returnCase.getInspectedAt(),
                    returnCase.getItems().stream().map(ReturnItemResponse::from).toList(),
                    RefundResponse.from(returnCase.getRefund()));
        }
    }
}
