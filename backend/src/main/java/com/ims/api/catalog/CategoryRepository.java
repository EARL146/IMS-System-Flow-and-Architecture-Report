package com.ims.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
