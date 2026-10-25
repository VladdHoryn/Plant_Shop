package com.plantshop.order.repository;

import com.plantshop.order.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String customerEmail);

    @Modifying
    @Query("update Order o set o.customerEmail = :erasedEmail, o.shippingAddress = :erasedAddress, o.updatedAt = :now "
            + "where lower(o.customerEmail) = lower(:email)")
    int anonymizeCustomer(String email, String erasedEmail, String erasedAddress, Instant now);
}
