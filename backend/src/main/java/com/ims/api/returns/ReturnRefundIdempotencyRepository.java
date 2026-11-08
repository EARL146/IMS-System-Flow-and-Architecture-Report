package com.ims.api.returns;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReturnRefundIdempotencyRepository extends JpaRepository<ReturnRefundIdempotencyKey, String> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO return_refund_idempotency_keys (idempotency_key, request_hash) VALUES (:key, :hash)", nativeQuery = true)
    int reserveIfAbsent(@Param("key") String key, @Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from ReturnRefundIdempotencyKey key where key.idempotencyKey = :key")
    Optional<ReturnRefundIdempotencyKey> findForUpdate(@Param("key") String key);
}
