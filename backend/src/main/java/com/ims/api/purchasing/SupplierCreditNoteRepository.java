package com.ims.api.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SupplierCreditNoteRepository extends JpaRepository<SupplierCreditNote, Long> {
    Optional<SupplierCreditNote> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(cn.amount), 0) from SupplierCreditNote cn where cn.supplierReturn.purchaseOrder.id = :purchaseOrderId")
    BigDecimal sumAmountByPurchaseOrderId(@Param("purchaseOrderId") long purchaseOrderId);
}

