package com.ims.api.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findAllByUser_IdOrderByDefaultAddressDescIdDesc(long userId);
    Optional<CustomerAddress> findByIdAndUser_Id(long id, long userId);
}
