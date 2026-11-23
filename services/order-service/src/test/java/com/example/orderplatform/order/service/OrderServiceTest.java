package com.example.orderplatform.order.service;

import com.example.orderplatform.order.domain.Order;
import com.example.orderplatform.order.domain.OrderStatus;
import com.example.orderplatform.order.messaging.PlatformEvent;
import com.example.orderplatform.order.messaging.Topics;
import com.example.orderplatform.order.metrics.OrderMetrics;
import com.example.orderplatform.order.repository.OrderRepository;
import com.example.orderplatform.order.repository.ProcessedEventRepository;
import com.example.orderplatform.order.web.ForbiddenException;
import com.example.orderplatform.order.web.dto.OrderRequest;
import com.example.orderplatform.order.web.dto.OrderResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxService outboxService;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private OrderMetrics metrics;
    @Mock private EntityManager entityManager;
    @InjectMocks private OrderService service;

    private final OrderRequest request = new OrderRequest(List.of(
            new OrderRequest.Item(1L, 2, new BigDecimal("9.99")),
            new OrderRequest.Item(2L, 1, new BigDecimal("4.00"))));

    private Order pendingOrder(String userId) {
        Instant now = Instant.now();
        Order order = Order.builder()
                .id(UUID.randomUUID()).userId(userId).status(OrderStatus.PENDING)
                .idempotencyKey("key-1").total(new BigDecimal("23.98"))
                .createdAt(now).updatedAt(now).build();
        order.addItem(com.example.orderplatform.order.domain.OrderItem.builder()
                .productId(1L).quantity(2).unitPrice(new BigDecimal("9.99")).build());
        return order;
    }

    @Test
    void createsPendingOrderWithOutboxEvent() {
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = service.create("user-1", "key-1", request);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.total()).isEqualByComparingTo("23.98");
        assertThat(response.items()).hasSize(2);
        verify(outboxService).append(eq(response.id()), eq(Topics.ORDER_CREATED), any(PlatformEvent.class));
        verify(metrics).orderCreated();
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalOrder() {
        Order original = pendingOrder("user-1");
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(original));

        OrderResponse response = service.create("user-1", "key-1", request);

        assertThat(response.id()).isEqualTo(original.getId());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(outboxService, never()).append(any(), any(), any());
    }

    @Test
    void stockReservedConfirmsOrderAndEmitsConfirmedEvent() {
        Order order = pendingOrder("user-1");
        PlatformEvent event = stockEvent(Topics.STOCK_RESERVED, order.getId());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.handleStockResult(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxService).append(eq(order.getId()), eq(Topics.ORDER_CONFIRMED), any(PlatformEvent.class));
    }

    @Test
    void replayedStockEventIsIgnored() {
        PlatformEvent event = stockEvent(Topics.STOCK_RESERVED, UUID.randomUUID());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        service.handleStockResult(event);

        verifyNoRepositoryMutation();
    }

    @Test
    void stockRejectedCancelsOrder() {
        Order order = pendingOrder("user-1");
        PlatformEvent event = stockEvent(Topics.STOCK_REJECTED, order.getId());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.handleStockResult(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxService, never()).append(any(), any(), any());
        verify(metrics).orderCancelled();
    }

    @Test
    void cancelEmitsOrderCancelledAndIsIdempotent() {
        Order order = pendingOrder("user-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.cancel(order.getId(), "user-1", false);
        service.cancel(order.getId(), "user-1", false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxService, times(1)).append(eq(order.getId()), eq(Topics.ORDER_CANCELLED), any(PlatformEvent.class));
    }

    @Test
    void nonOwnerCannotReadOrCancel() {
        Order order = pendingOrder("user-1");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.get(order.getId(), "user-2", false))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.cancel(order.getId(), "user-2", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCanReadAnyOrder() {
        Order order = pendingOrder("user-1");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(service.get(order.getId(), "admin-1", true).id()).isEqualTo(order.getId());
    }

    @Test
    void listsOnlyCurrentUsersOrders() {
        Order order = pendingOrder("user-1");
        when(orderRepository.findByUserId("user-1", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        var page = service.list("user-1", PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    private PlatformEvent stockEvent(String type, UUID orderId) {
        return new PlatformEvent(UUID.randomUUID(), type, Instant.now(), "corr-test", orderId,
                List.of(new PlatformEvent.Item(1L, 2)), null);
    }

    private void verifyNoRepositoryMutation() {
        verify(orderRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}
