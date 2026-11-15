package com.ims.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Optional<InventoryMovement> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            select movement from InventoryMovement movement
            join fetch movement.product
            join fetch movement.warehouse
            where movement.warehouse.id = :warehouseId
            order by movement.occurredAt desc
            """,
            countQuery = "select count(movement) from InventoryMovement movement where movement.warehouse.id = :warehouseId")
    Page<InventoryMovement> findRecentForWarehouse(@Param("warehouseId") long warehouseId, Pageable pageable);

    @Query("""
            select movement from InventoryMovement movement
            join fetch movement.product
            join fetch movement.warehouse
            order by movement.occurredAt asc, movement.id asc
            """)
    List<InventoryMovement> findAllOrdered();
}
