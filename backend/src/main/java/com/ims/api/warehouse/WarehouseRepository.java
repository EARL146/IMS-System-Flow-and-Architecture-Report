package com.ims.api.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<Warehouse> findAllByActiveTrueOrderByNameAsc();
}
