package com.ims.api.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Page<CustomerOrder> findByCustomer_IdOrderByCreatedAtDesc(long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "warehouse"})
    Page<CustomerOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "warehouse"})
    Page<CustomerOrder> findByStatusOrderByCreatedAtDesc(CustomerOrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "items", "items.product"})
    Optional<CustomerOrder> findDetailedByIdAndCustomer_Id(long id, long customerId);

    @EntityGraph(attributePaths = {"customer", "warehouse", "items", "items.product"})
    Optional<CustomerOrder> findDetailedById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct customerOrder from CustomerOrder customerOrder
            join fetch customerOrder.warehouse
            left join fetch customerOrder.items item
            left join fetch item.product
            where customerOrder.id = :orderId and customerOrder.customer.id = :customerId
            """)
    Optional<CustomerOrder> findDetailedForUpdateByIdAndCustomer(
            @Param("orderId") long orderId,
            @Param("customerId") long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct customerOrder from CustomerOrder customerOrder
            join fetch customerOrder.customer
            join fetch customerOrder.warehouse
            left join fetch customerOrder.items item
            left join fetch item.product
            where customerOrder.id = :orderId
            """)
    Optional<CustomerOrder> findDetailedForUpdateById(@Param("orderId") long orderId);
}
