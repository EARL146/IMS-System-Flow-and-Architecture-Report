package com.ims.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryIdempotencyRepository extends JpaRepository<InventoryIdempotencyKey, String> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO inventory_idempotency_keys (idempotency_key) VALUES (:key)", nativeQuery = true)
    int reserveIfAbsent(@Param("key") String key);
}
