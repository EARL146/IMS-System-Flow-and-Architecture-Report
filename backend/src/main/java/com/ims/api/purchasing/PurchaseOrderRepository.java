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

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy", "reviewedBy", "items", "items.product"})
    @Query("select distinct purchaseOrder from PurchaseOrder purchaseOrder order by purchaseOrder.createdAt desc")
    List<PurchaseOrder> findAllDetailed();
    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy", "reviewedBy", "items", "items.product"})
    @Query("select distinct purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy", "reviewedBy", "items", "items.product"})
    @Query("select distinct purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrder> findDetailedForUpdate(@Param("id") Long id);

    @Query("""
            select purchaseOrder from PurchaseOrder purchaseOrder
            order by purchaseOrder.createdAt desc, purchaseOrder.id desc
            """)
    Page<PurchaseOrder> findRecent(Pageable pageable);
}
