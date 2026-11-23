package com.example.orderplatform.inventory.repository;

import com.example.orderplatform.inventory.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Pessimistic write lock: concurrent reservations for the same product serialize here,
     * which prevents overselling. Always lock products in a stable (sorted) order to avoid
     * deadlocks when an order contains several products.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId = :productId")
    Optional<Stock> findForUpdate(@Param("productId") Long productId);
}
