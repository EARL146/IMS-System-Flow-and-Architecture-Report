package com.ims.api.returns;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.math.BigDecimal;

public interface ReturnCaseRepository extends JpaRepository<ReturnCase, Long> {
    Page<ReturnCase> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<ReturnCase> findByStatusOrderByRequestedAtDesc(ReturnStatus status, Pageable pageable);
    Page<ReturnCase> findAllByCustomer_IdOrderByRequestedAtDesc(long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customerOrder", "posSale", "customer", "requestedBy", "reviewedBy", "inspectedBy", "items", "items.product", "items.customerOrderItem", "items.posSaleItem", "refund", "refund.recordedBy"})
    Optional<ReturnCase> findDetailedById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customerOrder", "posSale", "customer", "requestedBy", "reviewedBy", "inspectedBy", "items", "items.product", "items.customerOrderItem", "items.posSaleItem", "refund", "refund.recordedBy"})
    @Query("select returnCase from ReturnCase returnCase where returnCase.id = :id")
    Optional<ReturnCase> findDetailedForUpdateById(@Param("id") long id);

    boolean existsByCustomerOrder_IdAndStatusIn(long orderId, Collection<ReturnStatus> statuses);
    boolean existsByCustomerOrder_IdAndStatus(long orderId, ReturnStatus status);
    boolean existsByPosSale_IdAndStatus(long saleId, ReturnStatus status);

    @Query("select coalesce(sum(returnCase.refundAmount), 0) from ReturnCase returnCase " +
            "where returnCase.customerOrder.id = :orderId and returnCase.id <> :excludedId " +
            "and returnCase.status in :statuses")
    BigDecimal allocatedRefundForOrder(@Param("orderId") long orderId, @Param("excludedId") long excludedId,
                                       @Param("statuses") Collection<ReturnStatus> statuses);

    @Query("select coalesce(sum(returnCase.refundAmount), 0) from ReturnCase returnCase " +
            "where returnCase.posSale.id = :saleId and returnCase.id <> :excludedId " +
            "and returnCase.status in :statuses")
    BigDecimal allocatedRefundForPosSale(@Param("saleId") long saleId, @Param("excludedId") long excludedId,
                                         @Param("statuses") Collection<ReturnStatus> statuses);
}
