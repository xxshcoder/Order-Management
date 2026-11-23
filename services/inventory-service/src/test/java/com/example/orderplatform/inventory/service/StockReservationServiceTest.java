package com.example.orderplatform.inventory.service;

import com.example.orderplatform.inventory.domain.Reservation;
import com.example.orderplatform.inventory.domain.ReservationStatus;
import com.example.orderplatform.inventory.domain.Stock;
import com.example.orderplatform.inventory.messaging.PlatformEvent;
import com.example.orderplatform.inventory.messaging.Topics;
import com.example.orderplatform.inventory.metrics.InventoryMetrics;
import com.example.orderplatform.inventory.repository.ProcessedEventRepository;
import com.example.orderplatform.inventory.repository.ReservationRepository;
import com.example.orderplatform.inventory.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private OutboxService outboxService;
    @Mock private InventoryMetrics metrics;
    @InjectMocks private StockReservationService service;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String CORR = "corr-123";

    private PlatformEvent event(long productId, int quantity) {
        return new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CREATED, Instant.now(), CORR, ORDER_ID,
                List.of(new PlatformEvent.Item(productId, quantity)), null);
    }

    private PlatformEvent multiItemEvent() {
        return new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CREATED, Instant.now(), CORR, ORDER_ID,
                List.of(new PlatformEvent.Item(2L, 3), new PlatformEvent.Item(1L, 2)), null);
    }

    @Test
    void reservesStockAndWritesSuccessEvent() {
        PlatformEvent event = event(1L, 3);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(stockRepository.findForUpdate(1L)).thenReturn(Optional.of(new Stock(1L, 10, 0)));

        service.reserve(event);

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isEqualTo(7);
        verify(reservationRepository).save(any(Reservation.class));
        verify(outboxService).append(eq(ORDER_ID), eq(Topics.STOCK_RESERVED), any(PlatformEvent.class));
        verify(processedEventRepository).save(any());
        verify(metrics, never()).reservationRejected();
    }

    @Test
    void rejectsWhenStockIsInsufficientWithNoPartialChanges() {
        PlatformEvent event = event(1L, 11);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(stockRepository.findForUpdate(1L)).thenReturn(Optional.of(new Stock(1L, 10, 0)));

        service.reserve(event);

        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxService).append(eq(ORDER_ID), eq(Topics.STOCK_REJECTED), any(PlatformEvent.class));
        verify(metrics).reservationRejected();
        verify(processedEventRepository).save(any());
    }

    @Test
    void multiItemReservationIsAllOrNothing() {
        PlatformEvent event = multiItemEvent();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(stockRepository.findForUpdate(1L)).thenReturn(Optional.of(new Stock(1L, 10, 0)));
        when(stockRepository.findForUpdate(2L)).thenReturn(Optional.of(new Stock(2L, 2, 0)));

        service.reserve(event);

        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxService).append(eq(ORDER_ID), eq(Topics.STOCK_REJECTED), any(PlatformEvent.class));
    }

    @Test
    void multiItemReservationLocksProductsInSortedOrder() {
        PlatformEvent event = multiItemEvent();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(stockRepository.findForUpdate(1L)).thenReturn(Optional.of(new Stock(1L, 10, 0)));
        when(stockRepository.findForUpdate(2L)).thenReturn(Optional.of(new Stock(2L, 10, 0)));

        service.reserve(event);

        var inOrder = org.mockito.Mockito.inOrder(stockRepository);
        inOrder.verify(stockRepository).findForUpdate(1L);
        inOrder.verify(stockRepository).findForUpdate(2L);
        verify(stockRepository, times(2)).save(any());
        verify(reservationRepository, times(2)).save(any());
        verify(outboxService).append(eq(ORDER_ID), eq(Topics.STOCK_RESERVED), any(PlatformEvent.class));
    }

    @Test
    void replayedEventChangesNothing() {
        PlatformEvent event = event(1L, 2);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        service.reserve(event);

        verifyNoInteractions(stockRepository, reservationRepository, outboxService, metrics);
    }

    @Test
    void releasesReservationsAndRestoresStock() {
        PlatformEvent event = event(1L, 0);
        Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID()).orderId(ORDER_ID).productId(1L).quantity(4)
                .status(ReservationStatus.RESERVED).createdAt(Instant.now()).build();
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(reservationRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepository.findForUpdate(1L)).thenReturn(Optional.of(new Stock(1L, 6, 0)));

        service.release(event);

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailable()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
}
