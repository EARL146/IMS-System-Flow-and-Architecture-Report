package com.ims.api.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierReturnIdempotencyRepository extends JpaRepository<SupplierReturnIdempotencyKey, String> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO supplier_return_idempotency_keys (idempotency_key, request_hash) VALUES (:key, :hash)", nativeQuery = true)
    int reserveIfAbsent(@Param("key") String key, @Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from SupplierReturnIdempotencyKey key where key.idempotencyKey = :key")
    Optional<SupplierReturnIdempotencyKey> findForUpdate(@Param("key") String key);
}
