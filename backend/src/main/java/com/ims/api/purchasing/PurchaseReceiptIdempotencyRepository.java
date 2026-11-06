package com.ims.api.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseReceiptIdempotencyRepository extends JpaRepository<PurchaseReceiptIdempotencyKey, String> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO purchase_receipt_idempotency (idempotency_key, request_hash) VALUES (:key, :hash)", nativeQuery = true)
    int reserveIfAbsent(@Param("key") String key, @Param("hash") String requestHash);
}
