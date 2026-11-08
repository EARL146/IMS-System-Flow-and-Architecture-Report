package com.ims.api.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ReturnRefundRepository extends JpaRepository<ReturnRefund, Long> {
    @Query("select coalesce(sum(refund.amount), 0) from ReturnRefund refund where refund.returnCase.customerOrder.id = :orderId")
    BigDecimal totalRefundedForOrder(@Param("orderId") long orderId);

    @Query("select coalesce(sum(refund.amount), 0) from ReturnRefund refund where refund.returnCase.posSale.id = :saleId")
    BigDecimal totalRefundedForPosSale(@Param("saleId") long saleId);
}
