package com.ims.api.customer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderManagementDtos {
    private OrderManagementDtos() {
    }

    public record StatusRequest(
            @NotNull CustomerOrderStatus status,
            @Size(max = 500) String note
    ) {
    }

    public record PaymentVerificationRequest(
            @NotNull UUID idempotencyKey,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes
    ) {
    }

    public record PaymentVerificationResponse(
            Long id, Long orderId, String orderNumber, PaymentMethod paymentMethod,
            String currencyCode, BigDecimal amount, String reference, String notes,
            String verifiedBy, Instant verifiedAt
    ) {
        static PaymentVerificationResponse from(CustomerPaymentVerification verification) {
            return new PaymentVerificationResponse(verification.getId(),
                    verification.getCustomerOrder().getId(), verification.getCustomerOrder().getOrderNumber(),
                    verification.getPaymentMethod(), verification.getCurrencyCode(), verification.getAmount(),
                    verification.getReference(), verification.getNotes(),
                    verification.getVerifiedBy().getFullName(), verification.getVerifiedAt());
        }
    }
}
