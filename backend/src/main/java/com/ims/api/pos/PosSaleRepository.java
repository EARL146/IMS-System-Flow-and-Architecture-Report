package com.ims.api.pos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PosSaleRepository extends JpaRepository<PosSale, Long> {
    @EntityGraph(attributePaths = {"cashier", "warehouse", "customer"})
    Page<PosSale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"cashier", "warehouse", "customer", "items", "items.product"})
    Optional<PosSale> findDetailedById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"cashier", "warehouse", "customer", "items", "items.product"})
    @Query("select sale from PosSale sale where sale.id = :id")
    Optional<PosSale> findDetailedForUpdateById(@Param("id") long id);
}
