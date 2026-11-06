package com.ims.api.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
            select p from Product p
            left join fetch p.category
            where p.active = true
              and (:query is null or :query = ''
                   or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(p.sku) like lower(concat('%', :query, '%'))
                   or lower(coalesce(p.barcode, '')) like lower(concat('%', :query, '%')))
            """)
    Page<Product> searchCatalog(@Param("query") String query, Pageable pageable);

    @Query("""
            select p from Product p left join fetch p.category
            where p.id = :id
            """)
    Optional<Product> findWithCategoryById(@Param("id") Long id);

    boolean existsBySkuIgnoreCase(String sku);
    boolean existsByBarcodeIgnoreCase(String barcode);
}
