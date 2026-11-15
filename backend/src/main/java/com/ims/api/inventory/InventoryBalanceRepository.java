package com.ims.api.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    interface AvailableStockProjection {
        Long getProductId();
        BigDecimal getAvailableQuantity();
    }

    @Query("""
            select balance.product.id as productId,
                   (balance.onHandQuantity - balance.reservedQuantity - balance.damagedQuantity) as availableQuantity
            from InventoryBalance balance
            where balance.warehouse.id = :warehouseId and balance.product.id in :productIds
            """)
    List<AvailableStockProjection> findAvailableByProductIds(
            @Param("warehouseId") long warehouseId,
            @Param("productIds") List<Long> productIds);

    @Modifying
    @Query(value = """
            INSERT INTO inventory_balances (product_id, warehouse_id, on_hand_quantity, reserved_quantity, damaged_quantity, version)
            VALUES (:productId, :warehouseId, 0, 0, 0, 0)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int ensureExists(@Param("productId") long productId, @Param("warehouseId") long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance from InventoryBalance balance
            join fetch balance.product
            join fetch balance.warehouse
            where balance.product.id = :productId and balance.warehouse.id = :warehouseId
            """)
    Optional<InventoryBalance> findForUpdate(
            @Param("productId") long productId,
            @Param("warehouseId") long warehouseId);

    @Query(value = """
            select balance from InventoryBalance balance
            join fetch balance.product
            join fetch balance.warehouse
            where balance.warehouse.id = :warehouseId
            order by balance.product.name
            """,
            countQuery = "select count(balance) from InventoryBalance balance where balance.warehouse.id = :warehouseId")
    Page<InventoryBalance> findAllForWarehouse(@Param("warehouseId") long warehouseId, Pageable pageable);

    @Query("select coalesce(sum(balance.onHandQuantity), 0) from InventoryBalance balance")
    BigDecimal totalOnHand();

    @Query("select coalesce(sum(balance.onHandQuantity - balance.reservedQuantity - balance.damagedQuantity), 0) from InventoryBalance balance")
    BigDecimal totalAvailable();

    @Query("""
            select balance from InventoryBalance balance
            join fetch balance.product
            join fetch balance.warehouse
            order by balance.warehouse.name, balance.product.name
            """)
    List<InventoryBalance> findAllDetailed();
}
