package com.plantshop.inventory.repository;

import com.plantshop.inventory.entity.ReservationStatus;
import com.plantshop.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);
}
