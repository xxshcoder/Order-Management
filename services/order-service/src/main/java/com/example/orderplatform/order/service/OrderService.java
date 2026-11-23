package com.example.orderplatform.order.service;

import com.example.orderplatform.order.domain.Order;
import com.example.orderplatform.order.domain.OrderItem;
import com.example.orderplatform.order.domain.OrderStatus;
import com.example.orderplatform.order.domain.ProcessedEvent;
import com.example.orderplatform.order.messaging.PlatformEvent;
import com.example.orderplatform.order.messaging.Topics;
import com.example.orderplatform.order.metrics.OrderMetrics;
import com.example.orderplatform.order.repository.OrderRepository;
import com.example.orderplatform.order.repository.ProcessedEventRepository;
import com.example.orderplatform.order.web.BadRequestException;
import com.example.orderplatform.order.web.ForbiddenException;
import com.example.orderplatform.order.web.NotFoundException;
import com.example.orderplatform.order.web.dto.OrderItemResponse;
import com.example.orderplatform.order.web.dto.OrderPage;
import com.example.orderplatform.order.web.dto.OrderRequest;
import com.example.orderplatform.order.web.dto.OrderResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderMetrics metrics;

    @PersistenceContext
    private EntityManager entityManager;

    /** Same (user, Idempotency-Key) always returns the original order. */
    public OrderResponse create(String userId, String idempotencyKey, OrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Returning existing order for idempotency key {}", idempotencyKey);
            return toResponse(existing.get());
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("An order needs at least one item");
        }

        Instant now = Instant.now();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<PlatformEvent.Item> eventItems = new ArrayList<>();
        for (OrderRequest.Item item : request.items()) {
            order.addItem(OrderItem.builder()
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .build());
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
            eventItems.add(new PlatformEvent.Item(item.productId(), item.quantity()));
        }
        order.setTotal(total);

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key: the loser re-reads the winner's order.
            entityManager.clear();
            saved = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new com.example.orderplatform.order.web.ConflictException(
                            "Idempotency key is already in use"));
            return toResponse(saved);
        }

        outboxService.append(saved.getId(), Topics.ORDER_CREATED,
                PlatformEvent.of(Topics.ORDER_CREATED, correlation(), saved.getId(), eventItems, null));
        metrics.orderCreated();
        log.info("Created order {} with {} items", saved.getId(), eventItems.size());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, String userId, boolean admin) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
        requireOwner(order, userId, admin);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderPage list(String userId, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserId(userId, pageable);
        return new OrderPage(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Cancels an order and emits order.cancelled so inventory releases the reservation. */
    public OrderResponse cancel(UUID id, String userId, boolean admin) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
        requireOwner(order, userId, admin);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        outboxService.append(order.getId(), Topics.ORDER_CANCELLED,
                PlatformEvent.of(Topics.ORDER_CANCELLED, correlation(), order.getId(),
                        eventItems(order), "Cancelled by user"));
        metrics.orderCancelled();
        log.info("Cancelled order {}", id);
        return toResponse(order);
    }

    /** Idempotently applies a stock result to a PENDING order. */
    public void handleStockResult(PlatformEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Ignoring already processed event {}", event.eventId());
            return;
        }
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order " + event.orderId() + " not found for event " + event.eventId()));

        if (order.getStatus() == OrderStatus.PENDING) {
            if (Topics.STOCK_RESERVED.equals(event.type())) {
                order.setStatus(OrderStatus.CONFIRMED);
                outboxService.append(order.getId(), Topics.ORDER_CONFIRMED,
                        PlatformEvent.of(Topics.ORDER_CONFIRMED, event.correlationId(), order.getId(),
                                eventItems(order), null));
                metrics.orderConfirmed();
            } else if (Topics.STOCK_REJECTED.equals(event.type())) {
                order.setStatus(OrderStatus.CANCELLED);
                metrics.orderCancelled();
            }
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }

    private void requireOwner(Order order, String userId, boolean admin) {
        if (!admin && !order.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not own order " + order.getId());
        }
    }

    private List<PlatformEvent.Item> eventItems(Order order) {
        return order.getItems().stream()
                .map(item -> new PlatformEvent.Item(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private String correlation() {
        String value = MDC.get("correlationId");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus().name(),
                order.getTotal(), items, order.getCreatedAt(), order.getUpdatedAt());
    }
}
