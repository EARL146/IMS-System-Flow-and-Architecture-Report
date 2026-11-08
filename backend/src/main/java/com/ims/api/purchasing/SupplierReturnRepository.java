package com.ims.api.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select supplierReturn from SupplierReturn supplierReturn where supplierReturn.id = :id")
    Optional<SupplierReturn> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "purchaseOrder.warehouse",
            "requestedBy", "reviewedBy", "dispatchedBy", "items", "items.purchaseOrderItem",
            "items.purchaseOrderItem.product", "creditNotes", "creditNotes.recordedBy"})
    @Query("select distinct supplierReturn from SupplierReturn supplierReturn where supplierReturn.id = :id")
    Optional<SupplierReturn> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.purchaseOrderItem"})
    List<SupplierReturn> findAllByPurchaseOrderId(Long purchaseOrderId);

    @Query("select supplierReturn from SupplierReturn supplierReturn order by supplierReturn.requestedAt desc, supplierReturn.id desc")
    Page<SupplierReturn> findRecent(Pageable pageable);

    Optional<SupplierReturn> findByIdempotencyKey(String idempotencyKey);
}
