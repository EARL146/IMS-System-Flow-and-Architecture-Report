package com.ims.api.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderStatusHistoryRepository extends JpaRepository<CustomerOrderStatusHistory, Long> {
    List<CustomerOrderStatusHistory> findAllByCustomerOrder_IdOrderByOccurredAtAscIdAsc(long orderId);
}
