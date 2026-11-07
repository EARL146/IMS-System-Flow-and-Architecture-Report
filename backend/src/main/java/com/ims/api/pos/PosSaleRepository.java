package com.ims.api.pos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PosSaleRepository extends JpaRepository<PosSale, Long> {
    @EntityGraph(attributePaths = {"cashier", "warehouse", "customer"})
    Page<PosSale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"cashier", "warehouse", "customer", "items", "items.product"})
    Optional<PosSale> findDetailedById(long id);
}
