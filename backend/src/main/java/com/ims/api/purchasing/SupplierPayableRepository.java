package com.ims.api.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierPayableRepository extends JpaRepository<SupplierPayable, Long> {
    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "createdBy", "payments", "payments.recordedBy"})
    @Query("select p from SupplierPayable p order by p.createdAt desc, p.id desc")
    Page<SupplierPayable> findRecent(Pageable pageable);

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "createdBy", "payments", "payments.recordedBy"})
    @Query("select p from SupplierPayable p where p.id = :id")
    Optional<SupplierPayable> findDetailedById(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "createdBy", "payments", "payments.recordedBy"})
    @Query("select p from SupplierPayable p where p.id = :id")
    Optional<SupplierPayable> findForUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "createdBy", "payments", "payments.recordedBy"})
    Optional<SupplierPayable> findByPurchaseOrder_Id(long purchaseOrderId);
}
