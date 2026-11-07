package com.ims.api.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerPaymentVerificationRepository extends JpaRepository<CustomerPaymentVerification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select verification from CustomerPaymentVerification verification where verification.idempotencyKey = :key")
    Optional<CustomerPaymentVerification> findByIdempotencyKeyForUpdate(@Param("key") String key);

    Optional<CustomerPaymentVerification> findByCustomerOrder_Id(long customerOrderId);
}
