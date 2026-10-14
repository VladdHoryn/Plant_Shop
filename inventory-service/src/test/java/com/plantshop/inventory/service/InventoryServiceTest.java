package com.plantshop.inventory.service;

import com.plantshop.inventory.entity.InventoryItem;
import com.plantshop.inventory.entity.ReservationStatus;
import com.plantshop.inventory.entity.StockReservation;
import com.plantshop.inventory.event.InventoryFailedEvent;
import com.plantshop.inventory.event.InventoryReservedEvent;
import com.plantshop.inventory.event.OrderCreatedEvent;
import com.plantshop.inventory.repository.InventoryItemRepository;
import com.plantshop.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private InventoryService inventoryService;

    private static InventoryItem item(long plantId, int quantity) {
        InventoryItem item = new InventoryItem(plantId);
        item.setQuantity(quantity);
        return item;
    }

    private static OrderCreatedEvent order(int quantity) {
        return new OrderCreatedEvent(7L, "a@b.com", new BigDecimal("50.00"), "CARD",
                List.of(new OrderCreatedEvent.Item(1L, quantity)));
    }

    @Test
    void reserveHoldsStockWhenAvailable() {
        InventoryItem monstera = item(1L, 10);
        when(itemRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(monstera));

        inventoryService.reserve(order(3));

        assertThat(monstera.getReserved()).isEqualTo(3);
        assertThat(monstera.getAvailable()).isEqualTo(7);
        verify(reservationRepository).save(any(StockReservation.class));
        verify(eventPublisher).publishEvent(new InventoryReservedEvent(7L, "a@b.com", new BigDecimal("50.00"), "CARD"));
    }

    @Test
    void reserveFailsWithoutTouchingStockWhenInsufficient() {
        InventoryItem monstera = item(1L, 2);
        when(itemRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(monstera));

        inventoryService.reserve(order(3));

        assertThat(monstera.getReserved()).isZero();
        verify(reservationRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(InventoryFailedEvent.class));
    }

    @Test
    void commitDeductsReservedStock() {
        InventoryItem monstera = item(1L, 10);
        monstera.reserve(3);
        StockReservation reservation = new StockReservation(7L, 1L, 3);
        when(reservationRepository.findByOrderIdAndStatus(7L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(itemRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(monstera));

        inventoryService.commitReservations(7L);

        assertThat(monstera.getQuantity()).isEqualTo(7);
        assertThat(monstera.getReserved()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }
}
