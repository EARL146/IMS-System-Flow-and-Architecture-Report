package com.ims.api.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CheckoutIdempotencyKeyRepository extends JpaRepository<CheckoutIdempotencyKey, Long> {
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO checkout_idempotency_keys (user_id, idempotency_key, request_hash)
            VALUES (:userId, :idempotencyKey, :requestHash)
            """, nativeQuery = true)
    int reserveIfAbsent(
            @Param("userId") long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from CheckoutIdempotencyKey key where key.user.id = :userId and key.idempotencyKey = :idempotencyKey")
    Optional<CheckoutIdempotencyKey> findForUpdate(
            @Param("userId") long userId,
            @Param("idempotencyKey") String idempotencyKey);
}
