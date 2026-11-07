package com.ims.api.pos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PosSaleIdempotencyRepository extends JpaRepository<PosSaleIdempotencyKey, String> {
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO pos_sale_idempotency_keys (idempotency_key, request_hash)
            VALUES (:key, :requestHash)
            """, nativeQuery = true)
    int reserveIfAbsent(@Param("key") String key, @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from PosSaleIdempotencyKey key where key.idempotencyKey = :key")
    Optional<PosSaleIdempotencyKey> findForUpdate(@Param("key") String key);
}
