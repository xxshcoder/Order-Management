package com.example.orderplatform.inventory.service;

import com.example.orderplatform.inventory.domain.ProcessedEvent;
import com.example.orderplatform.inventory.domain.Reservation;
import com.example.orderplatform.inventory.domain.ReservationStatus;
import com.example.orderplatform.inventory.domain.Stock;
import com.example.orderplatform.inventory.messaging.PlatformEvent;
import com.example.orderplatform.inventory.messaging.Topics;
import com.example.orderplatform.inventory.metrics.InventoryMetrics;
import com.example.orderplatform.inventory.repository.ProcessedEventRepository;
import com.example.orderplatform.inventory.repository.ReservationRepository;
import com.example.orderplatform.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxService outboxService;
    private final InventoryMetrics metrics;

    /**
     * Reserves all items of an order or none of them. Stock rows are locked in sorted
     * product-id order so multi-item orders cannot deadlock each other. Replaying the
     * same event is a no-op thanks to processed_events.
     */
    @Transactional
    public void reserve(PlatformEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Ignoring already processed event {}", event.eventId());
            return;
        }

        List<PlatformEvent.Item> items = event.items() == null ? List.of() : event.items();
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (PlatformEvent.Item item : items) {
            if (item != null && item.productId() != null && item.quantity() > 0) {
                requested.merge(item.productId(), item.quantity(), Integer::sum);
            }
        }

        boolean canReserve = !requested.isEmpty();
        List<Long> productIds = new ArrayList<>(requested.keySet());
        productIds.sort(Long::compareTo);

        Map<Long, Stock> locked = new LinkedHashMap<>();
        for (Long productId : productIds) {
            Optional<Stock> found = stockRepository.findForUpdate(productId);
            if (found.isEmpty()) {
                canReserve = false;
                break;
            }
            locked.put(productId, found.get());
        }

        if (canReserve) {
            for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
                Stock stock = locked.get(entry.getKey());
                if (stock == null || stock.getAvailable() < entry.getValue()) {
                    canReserve = false;
                    break;
                }
            }
        }

        if (canReserve) {
            for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
                Stock stock = locked.get(entry.getKey());
                stock.setAvailable(stock.getAvailable() - entry.getValue());
                stockRepository.save(stock);
                reservationRepository.save(Reservation.builder()
                        .id(UUID.randomUUID())
                        .orderId(event.orderId())
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .status(ReservationStatus.RESERVED)
                        .createdAt(Instant.now())
                        .build());
            }
            outboxService.append(event.orderId(), Topics.STOCK_RESERVED,
                    PlatformEvent.of(Topics.STOCK_RESERVED, event.correlationId(), event.orderId(), items, null));
            log.info("Reserved stock for order {}", event.orderId());
        } else {
            outboxService.append(event.orderId(), Topics.STOCK_REJECTED,
                    PlatformEvent.of(Topics.STOCK_REJECTED, event.correlationId(), event.orderId(), items,
                            "Insufficient stock or unknown product"));
            metrics.reservationRejected();
            log.warn("Rejected stock reservation for order {}", event.orderId());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }

    /** Releases every still-RESERVED row for the order and restores stock. Idempotent. */
    @Transactional
    public void release(PlatformEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Ignoring already processed event {}", event.eventId());
            return;
        }

        List<Reservation> reservations =
                reservationRepository.findByOrderIdAndStatus(event.orderId(), ReservationStatus.RESERVED);
        List<Long> productIds = reservations.stream().map(Reservation::getProductId).distinct().sorted().toList();

        Map<Long, Stock> locked = new LinkedHashMap<>();
        for (Long productId : productIds) {
            stockRepository.findForUpdate(productId).ifPresent(stock -> locked.put(productId, stock));
        }

        for (Reservation reservation : reservations) {
            Stock stock = locked.get(reservation.getProductId());
            if (stock != null) {
                stock.setAvailable(stock.getAvailable() + reservation.getQuantity());
                stockRepository.save(stock);
            } else {
                log.warn("No stock row while releasing reservation {} for product {}",
                        reservation.getId(), reservation.getProductId());
            }
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
        log.info("Released {} reservations for order {}", reservations.size(), event.orderId());
    }
}
