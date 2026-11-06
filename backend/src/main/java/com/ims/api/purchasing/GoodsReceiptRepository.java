package com.ims.api.purchasing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    @EntityGraph(attributePaths = {"purchaseOrder", "receivedBy", "items", "items.purchaseOrderItem", "items.purchaseOrderItem.product"})
    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);
}
