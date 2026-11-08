package com.ims.api.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierCreditNoteRepository extends JpaRepository<SupplierCreditNote, Long> {
    Optional<SupplierCreditNote> findByIdempotencyKey(String idempotencyKey);
}
