package com.example.orderplatform.inventory.repository;

import com.example.orderplatform.inventory.domain.Reservation;
import com.example.orderplatform.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, java.util.UUID> {

    List<Reservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
}
