package com.ims.api.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ReturnCaseItemRepository extends JpaRepository<ReturnCaseItem, Long> {
    @Query("select coalesce(sum(case when item.returnCase.status = com.ims.api.returns.ReturnStatus.REQUESTED then item.requestedQuantity else item.approvedQuantity end), 0) " +
            "from ReturnCaseItem item where item.customerOrderItem.id = :sourceItemId and item.returnCase.status <> :rejected")
    BigDecimal requestedForOrderItem(@Param("sourceItemId") long sourceItemId, @Param("rejected") ReturnStatus rejected);

    @Query("select coalesce(sum(case when item.returnCase.status = com.ims.api.returns.ReturnStatus.REQUESTED then item.requestedQuantity else item.approvedQuantity end), 0) " +
            "from ReturnCaseItem item where item.posSaleItem.id = :sourceItemId and item.returnCase.status <> :rejected")
    BigDecimal requestedForPosItem(@Param("sourceItemId") long sourceItemId, @Param("rejected") ReturnStatus rejected);
}
