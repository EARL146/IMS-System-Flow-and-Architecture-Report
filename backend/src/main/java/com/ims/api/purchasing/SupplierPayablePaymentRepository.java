package com.ims.api.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierPayablePaymentRepository extends JpaRepository<SupplierPayablePayment, Long> {
    Optional<SupplierPayablePayment> findByIdempotencyKey(String idempotencyKey);
}
