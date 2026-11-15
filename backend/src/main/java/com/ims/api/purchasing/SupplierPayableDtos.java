package com.ims.api.purchasing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SupplierPayableDtos {
    private SupplierPayableDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "purchaseOrderId is required") Long purchaseOrderId,
            @Size(max = 120) String invoiceReference,
            LocalDate dueDate,
            @Size(max = 500) String notes
    ) {
    }

    public record PaymentRequest(
            @NotNull(message = "idempotencyKey is required") UUID idempotencyKey,
            @NotBlank(message = "paymentReference is required") @Size(max = 120) String paymentReference,
            @NotNull(message = "paymentMethod is required") SupplierPayablePaymentMethod paymentMethod,
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
            @Size(max = 500) String notes
    ) {
    }

    public record PaymentResponse(
            long id,
            String paymentReference,
            SupplierPayablePaymentMethod paymentMethod,
            BigDecimal amount,
            String currencyCode,
            String notes,
            String recordedByEmail,
            Instant paidAt
    ) {
        static PaymentResponse from(SupplierPayablePayment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getPaymentReference(),
                    payment.getPaymentMethod(),
                    payment.getAmount(),
                    payment.getCurrencyCode(),
                    payment.getNotes(),
                    payment.getRecordedBy().getEmail(),
                    payment.getPaidAt()
            );
        }
    }

    public record Response(
            long id,
            String payableNumber,
            long purchaseOrderId,
            String purchaseOrderNumber,
            long supplierId,
            String supplierName,
            String invoiceReference,
            SupplierPayableStatus status,
            String currencyCode,
            BigDecimal grossAmount,
            BigDecimal creditAppliedAmount,
            BigDecimal netPayableAmount,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            LocalDate dueDate,
            String notes,
            String createdByEmail,
            Instant createdAt,
            List<PaymentResponse> payments
    ) {
        static Response from(SupplierPayable payable) {
            return new Response(
                    payable.getId(),
                    payable.getPayableNumber(),
                    payable.getPurchaseOrder().getId(),
                    payable.getPurchaseOrder().getOrderNumber(),
                    payable.getSupplier().getId(),
                    payable.getSupplier().getName(),
                    payable.getInvoiceReference(),
                    payable.getStatus(),
                    payable.getCurrencyCode(),
                    payable.getGrossAmount(),
                    payable.getCreditAppliedAmount(),
                    payable.getNetPayableAmount(),
                    payable.getAmountPaid(),
                    payable.getBalanceDue(),
                    payable.getDueDate(),
                    payable.getNotes(),
                    payable.getCreatedBy().getEmail(),
                    payable.getCreatedAt(),
                    payable.getPayments().stream().map(PaymentResponse::from).toList()
            );
        }
    }
}
