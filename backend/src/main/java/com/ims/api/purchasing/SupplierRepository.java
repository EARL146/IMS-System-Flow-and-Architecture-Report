package com.ims.api.purchasing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("""
            select supplier from Supplier supplier
            where :query is null or :query = ''
               or lower(supplier.name) like lower(concat('%', :query, '%'))
               or lower(supplier.code) like lower(concat('%', :query, '%'))
            """)
    Page<Supplier> search(@Param("query") String query, Pageable pageable);
}
