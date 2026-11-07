package com.ims.api.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @EntityGraph(attributePaths = "product")
    List<CartItem> findAllByUser_IdOrderByIdAsc(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from CartItem item where item.user.id = :userId and item.product.id = :productId")
    Optional<CartItem> findForUpdateByUserAndProduct(
            @Param("userId") long userId,
            @Param("productId") long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from CartItem item join fetch item.product where item.user.id = :userId order by item.id")
    List<CartItem> findAllForUpdateByUser(@Param("userId") long userId);

    Optional<CartItem> findByIdAndUser_Id(long id, long userId);
    long deleteByUser_Id(long userId);
}
